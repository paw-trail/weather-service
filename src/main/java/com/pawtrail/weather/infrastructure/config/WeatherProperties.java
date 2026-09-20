package com.pawtrail.weather.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 날씨 서비스 설정입니다. 값은 config 저장소 weather-service.yml 에 있습니다.
 *
 * 비어 있으면 기동을 막습니다. 비어 있는 채로 떠서 첫 요청에 실패하는 것보다 뜨지 않는 편이 낫습니다.
 * 검증을 바꾸면 테스트 리소스의 application.yml 사본도 함께 봐야 합니다 (설정 서버를 꺼서 값이 안 내려옴).
 */
@Validated
@ConfigurationProperties(prefix = "app.weather")
public record WeatherProperties(

        @NotNull(message = "app.weather.kma 가 필요합니다")
        @Valid
        Kma kma,

        @NotNull(message = "app.weather.cache 가 필요합니다")
        @Valid
        Cache cache,

        @NotNull(message = "app.weather.circuit-breaker 가 필요합니다")
        @Valid
        Breaker circuitBreaker
) {

    /**
     * 기상청 단기예보 조회서비스입니다.
     *
     * @param baseUrl        서비스 주소입니다. 오퍼레이션 이름은 코드가 붙입니다.
     * @param serviceKey     공공데이터포털 인증키입니다. 원본(Decoding) 키를 둡니다. 코드가 한 번 인코딩합니다.
     * @param numOfRows      한 번에 받을 행 수입니다. 발표 한 번의 앞쪽 며칠이 담기면 됩니다.
     * @param maxAttempts    시도 횟수입니다. 첫 시도를 포함합니다.
     * @param retryBackoffMs 다시 시도하기 전 기다리는 시간입니다. 시도마다 두 배가 됩니다.
     */
    public record Kma(
            @NotBlank(message = "app.weather.kma.base-url 이 필요합니다")
            String baseUrl,

            @NotBlank(message = "app.weather.kma.service-key 가 필요합니다 — WEATHER_PUBLIC_DATA_SERVICE_KEY 환경변수")
            String serviceKey,

            @NotNull(message = "app.weather.kma.num-of-rows 가 필요합니다")
            @Positive(message = "app.weather.kma.num-of-rows 는 양수여야 합니다")
            @Max(value = 1000, message = "app.weather.kma.num-of-rows 는 1000 을 넘을 수 없습니다")
            Integer numOfRows,

            @NotNull(message = "app.weather.kma.max-attempts 가 필요합니다")
            @Positive(message = "app.weather.kma.max-attempts 는 양수여야 합니다")
            @Max(value = 5, message = "app.weather.kma.max-attempts 는 5 를 넘을 수 없습니다")
            Integer maxAttempts,

            @NotNull(message = "app.weather.kma.retry-backoff-ms 가 필요합니다")
            @PositiveOrZero(message = "app.weather.kma.retry-backoff-ms 는 0 이상이어야 합니다")
            Long retryBackoffMs
    ) {
    }

    /**
     * @param ttlHours 발표 하나를 캐시에 두는 시간입니다.
     *                 가장 새 발표로 3시간 · 그다음 직전 발표로 3시간 쓰이므로 6시간보다 조금 길게 둡니다.
     */
    public record Cache(
            @NotNull(message = "app.weather.cache.ttl-hours 가 필요합니다")
            @Positive(message = "app.weather.cache.ttl-hours 는 양수여야 합니다")
            Integer ttlHours
    ) {
    }

    /**
     * 기상청 호출에 거는 회로 차단기입니다.
     *
     * @param slidingWindowSize               최근 몇 번의 호출로 실패율을 셀지입니다.
     * @param minimumNumberOfCalls            실패율을 세기 시작하는 최소 호출 수입니다.
     * @param failureRateThreshold            이 비율(%) 이상 실패하면 엽니다.
     * @param waitSecondsInOpenState          열린 뒤 기상청을 안 부르는 시간(초)입니다.
     * @param permittedCallsInHalfOpenState   그 뒤 시험 삼아 흘려보낼 호출 수입니다.
     */
    public record Breaker(
            @NotNull(message = "app.weather.circuit-breaker.sliding-window-size 가 필요합니다")
            @Positive(message = "app.weather.circuit-breaker.sliding-window-size 는 양수여야 합니다")
            Integer slidingWindowSize,

            @NotNull(message = "app.weather.circuit-breaker.minimum-number-of-calls 가 필요합니다")
            @Positive(message = "app.weather.circuit-breaker.minimum-number-of-calls 는 양수여야 합니다")
            Integer minimumNumberOfCalls,

            @NotNull(message = "app.weather.circuit-breaker.failure-rate-threshold 가 필요합니다")
            @Positive(message = "app.weather.circuit-breaker.failure-rate-threshold 는 양수여야 합니다")
            @Max(value = 100, message = "app.weather.circuit-breaker.failure-rate-threshold 는 100 을 넘을 수 없습니다")
            Integer failureRateThreshold,

            @NotNull(message = "app.weather.circuit-breaker.wait-seconds-in-open-state 가 필요합니다")
            @Positive(message = "app.weather.circuit-breaker.wait-seconds-in-open-state 는 양수여야 합니다")
            Integer waitSecondsInOpenState,

            @NotNull(message = "app.weather.circuit-breaker.permitted-calls-in-half-open-state 가 필요합니다")
            @Positive(message = "app.weather.circuit-breaker.permitted-calls-in-half-open-state 는 양수여야 합니다")
            Integer permittedCallsInHalfOpenState
    ) {
    }
}
