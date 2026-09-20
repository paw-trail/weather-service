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
 *   다시 시도함    시간 초과 · 연결 실패 · 5xx · 기상청 일시 오류 코드 (01 · 02 · 04 · 05 · 99)
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
                    String why = failure instanceof CallNotPermittedException
                            ? "회로 차단기가 열려 있어 부르지 않음"
                            : "기상청 호출 실패 — " + failure.getMessage();
                    throw new ForecastUnavailableException(why, failure);
                });
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
                KmaResponse response = KmaResponseParser.parse(e.getResponseBodyAsString(), grid, baseAt);
                boolean retryable = e.getStatusCode().is5xxServerError()
                        || response.status() == KmaResponse.Status.RETRYABLE;
                if (!retryable) {
                    throw new KmaApiException(response.code(), "HTTP " + e.getStatusCode().value()
                            + " " + response.message(), false, e);
                }
                last = new KmaApiException(response.code(), "HTTP " + e.getStatusCode().value(), true, e);
            } catch (ResourceAccessException e) {
                last = new KmaApiException("IO", "연결 실패 · 시간 초과 — " + e.getMessage(), true, e);
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

    // 로그에 인증키가 남지 않게 가림
    private static String masked(URI uri) {
        return uri.toString().replaceAll("(serviceKey=)[^&]+", "$1***");
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
