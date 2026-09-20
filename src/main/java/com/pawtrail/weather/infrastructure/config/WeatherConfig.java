package com.pawtrail.weather.infrastructure.config;

import com.pawtrail.weather.domain.rule.RegionGridTable;
import com.pawtrail.weather.infrastructure.persistence.RegionGridCsvReader;
import com.pawtrail.weather.infrastructure.provider.external.KmaForecastProviderImpl;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * 날씨 서비스의 빈을 모읍니다.
 */
@Configuration
@EnableConfigurationProperties(WeatherProperties.class)
public class WeatherConfig {

    // 지금 시각을 여기서만 얻음 — 발표 시각 계산을 시험에서 고정된 시각으로 돌리려고 둠
    // 컨테이너는 TZ=Asia/Seoul 이라 기본 시간대가 곧 한국 시각임 (기상청 발표 시각도 한국 시각)
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    // 기상청 격자 표 — 앱이 뜰 때 한 번 읽음 · 파일이 깨졌으면 기동을 막음
    @Bean
    public RegionGridTable regionGridTable() {
        return RegionGridCsvReader.read(new ClassPathResource(RegionGridCsvReader.LOCATION));
    }

    // 기상청 호출에 거는 회로 차단기 설정
    //
    // * 스프링 클라우드 추상화(CircuitBreakerFactory)에 이 이름의 설정을 얹음
    //   기본값(최소 100번을 보고 판단)으로는 우리 호출 수에서 거의 열리지 않아 작게 잡음
    //
    // * 시간 제한기는 config 에서 끔 (spring.cloud.circuitbreaker.resilience4j.disable-time-limiter)
    //   켜 두면 기본 1초에 끊기고 호출이 다른 스레드로 넘어감 · 제한 시간은 공통 모듈의 RestClient 가 맡음
    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> kmaCircuitBreakerCustomizer(WeatherProperties properties) {
        WeatherProperties.Breaker breaker = properties.circuitBreaker();
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(breaker.slidingWindowSize())
                .minimumNumberOfCalls(breaker.minimumNumberOfCalls())
                .failureRateThreshold(breaker.failureRateThreshold())
                .waitDurationInOpenState(Duration.ofSeconds(breaker.waitSecondsInOpenState()))
                .permittedNumberOfCallsInHalfOpenState(breaker.permittedCallsInHalfOpenState())
                .build();
        return factory -> factory.configure(
                builder -> builder.circuitBreakerConfig(config),
                KmaForecastProviderImpl.CIRCUIT_BREAKER_ID);
    }
}
