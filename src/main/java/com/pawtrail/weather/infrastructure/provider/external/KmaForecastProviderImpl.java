package com.pawtrail.weather.infrastructure.provider.external;

import com.pawtrail.weather.domain.exception.ForecastUnavailableException;
import com.pawtrail.weather.domain.model.ForecastRun;
import com.pawtrail.weather.domain.model.Grid;
import com.pawtrail.weather.domain.provider.ForecastProvider;
import com.pawtrail.weather.infrastructure.config.WeatherProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 기상청 단기예보(getVilageFcst)를 부릅니다.
 *
 * 호출 하나를 회로 차단기로 감싸고, 그 안에서 일시적인 실패에만 다시 시도합니다.
 *   다시 시도함    시간 초과 · 연결 실패 · 5xx · 서비스 연결 실패 오류 코드 05
 *   다시 안 함     인증키 · 파라미터 · 요청 한도 — 다시 불러도 같고 한도만 씀 (ingest 선례)
 *   실패 아님      03 자료 없음 — 빈 값으로 돌려 서비스가 직전 발표를 쓰게 함
 * 차단기가 열려 있으면 기상청을 아예 부르지 않고 바로 ForecastUnavailableException 을 던집니다.
 * 장애가 요청마다 시간 제한을 기다리게 만들지 않는 것이 차단기를 둔 까닭입니다.
 *
 * 인증키는 원본을 한 번만 인코딩해 주소에 붙이고, 완성된 URI 를 그대로 넘겨 다시 인코딩되지 않게 합니다.
 * 두 번 인코딩되면 %2B 가 %252B 가 되어 등록되지 않은 키라고 답합니다 (ingest PetTourApiClient 선례).
 */
@Component
public class KmaForecastProviderImpl implements ForecastProvider {

    private static final Logger log = LoggerFactory.getLogger(KmaForecastProviderImpl.class);

    // 회로 차단기 이름 — WeatherConfig 가 이 이름에 설정을 얹고 지표에도 이 이름으로 남음
    public static final String CIRCUIT_BREAKER_ID = "kma";

    private static final String OPERATION = "/getVilageFcst";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmm");

    private final RestClient restClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final WeatherProperties.Kma kma;
    private final String encodedServiceKey;

    // 바깥 API 용 빌더를 씀 — 인증 헤더 인터셉터가 없고 공통 제한 시간(연결 2초 · 읽기 5초)만 있음
    // @Qualifier 가 빠지면 아무것도 없는 기본 빌더가 조용히 들어오므로 생성자를 손으로 씀
    public KmaForecastProviderImpl(@Qualifier("externalRestClientBuilder") RestClient.Builder builder,
                                   CircuitBreakerFactory<?, ?> circuitBreakerFactory,
                                   WeatherProperties properties) {
        this.restClient = builder.build();
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.kma = properties.kma();
        this.encodedServiceKey = URLEncoder.encode(kma.serviceKey(), StandardCharsets.UTF_8);
    }

    @Override
    public Optional<ForecastRun> fetch(Grid grid, LocalDateTime baseAt) {
        return circuitBreakerFactory.create(CIRCUIT_BREAKER_ID).run(
                () -> fetchWithRetry(grid, baseAt),
                failure -> {
                    throw new ForecastUnavailableException(reason(failure), failure);
                });
    }

    /**
     * 서비스 로그에 남길 실패 까닭입니다.
     *
     * 회로 차단기는 공급자가 던진 예외를 한 겹 감싸 넘기므로 그 문구를 그대로 쓰면
     * 앞에 예외 클래스 이름이 붙습니다 (2026.9.20 실물). 사슬을 따라 원래 예외를 찾아 그 코드와 문구만 적습니다.
     */
    static String reason(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof CallNotPermittedException) {
                return "회로 차단기가 열려 있어 부르지 않음";
            }
            if (cause instanceof KmaApiException kma) {
                return "기상청 호출 실패 — 코드 " + kma.getCode() + " · " + kma.getMessage();
            }
            if (cause.getCause() == null) {
                return "기상청 호출 실패 — " + cause;
            }
        }
        return "기상청 호출 실패";
    }

    private Optional<ForecastRun> fetchWithRetry(Grid grid, LocalDateTime baseAt) {
        URI uri = uri(grid, baseAt);
        KmaApiException last = null;
        for (int attempt = 1; attempt <= kma.maxAttempts(); attempt++) {
            try {
                String body = restClient.get().uri(uri).retrieve().body(String.class);
                KmaResponse response = KmaResponseParser.parse(body, grid, baseAt);
                switch (response.status()) {
                    case OK:
                        return Optional.of(response.run());
                    case NO_DATA:
                        return Optional.empty();
                    case PERMANENT:
                        throw new KmaApiException(response.code(), response.message(), false);
                    default:
                        last = new KmaApiException(response.code(), response.message(), true);
                }
            } catch (RestClientResponseException e) {
                // 4xx 는 본문이 기상청 일시 오류 코드를 댈 때만 다시 시도함 — 읽지 못한 4xx 를 되풀이하지 않음
                // 틀린 키는 HTTP 403 과 게이트웨이 봉투(30)로 옴 (2026.9.20 실물)
                KmaResponse response = KmaResponseParser.parse(e.getResponseBodyAsString(), grid, baseAt);
                boolean retryable = e.getStatusCode().is5xxServerError()
                        || KmaResponseParser.RETRYABLE_CODES.contains(response.code());
                String message = "HTTP " + e.getStatusCode().value() + " · " + response.message();
                if (!retryable) {
                    throw new KmaApiException(response.code(), message, false, e);
                }
                last = new KmaApiException(response.code(), message, true, e);
            } catch (ResourceAccessException e) {
                // 이 예외의 문구에는 요청 주소가 통째로 들어 있어 인증키를 가려서 담음
                last = new KmaApiException("IO", "연결 실패 · 시간 초과 — " + maskKey(e.getMessage()), true, e);
            }
            log.warn("기상청 호출 실패 {}/{} 코드={} 까닭={} uri={}",
                    attempt, kma.maxAttempts(), last.getCode(), last.getMessage(), masked(uri));
            if (attempt < kma.maxAttempts()) {
                sleep(kma.retryBackoffMs() << (attempt - 1));
            }
        }
        throw last;
    }

    private URI uri(Grid grid, LocalDateTime baseAt) {
        String query = "serviceKey=" + encodedServiceKey
                + "&pageNo=1"
                + "&numOfRows=" + kma.numOfRows()
                + "&dataType=JSON"
                + "&base_date=" + baseAt.format(DATE)
                + "&base_time=" + baseAt.format(TIME)
                + "&nx=" + grid.nx()
                + "&ny=" + grid.ny();
        return URI.create(kma.baseUrl() + OPERATION + "?" + query);
    }

    private static String masked(URI uri) {
        return maskKey(uri.toString());
    }

    // 로그 · 예외 문구에 인증키가 남지 않게 가림
    static String maskKey(String text) {
        return text == null ? null : text.replaceAll("(serviceKey=)[^&\\s\"]+", "$1***");
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KmaApiException("INTERRUPTED", "다시 시도하기 전에 중단됨", false, e);
        }
    }
}
