package com.pawtrail.weather.infrastructure.provider.external;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 서비스 로그에 남는 실패 까닭과 인증키 가리기를 봅니다. 기상청 호출 자체는 실물로 확인합니다.
 */
class KmaForecastProviderImplTest {

    @Test
    @DisplayName("차단기가 감싸 넘긴 예외를 벗겨 기상청 코드와 문구만 적음")
    void 감싼_예외() {
        KmaApiException cause = new KmaApiException("30",
                "HTTP 403 · SERVICE_KEY_IS_NOT_REGISTERED_ERROR · 등록되지 않은 서비스키", false);

        assertThat(KmaForecastProviderImpl.reason(new RuntimeException(cause)))
                .isEqualTo("기상청 호출 실패 — 코드 30 · HTTP 403 · SERVICE_KEY_IS_NOT_REGISTERED_ERROR · 등록되지 않은 서비스키");
    }

    @Test
    @DisplayName("차단기가 열려 있으면 그 사실만 적음")
    void 열린_차단기() {
        CircuitBreaker breaker = CircuitBreaker.ofDefaults("kma");
        breaker.transitionToOpenState();

        assertThat(KmaForecastProviderImpl.reason(CallNotPermittedException.createCallNotPermittedException(breaker)))
                .isEqualTo("회로 차단기가 열려 있어 부르지 않음");
    }

    @Test
    @DisplayName("모르는 예외의 문구에 요청 주소가 들어 있어도 까닭에서는 인증키를 가림 (CodeRabbit 지적)")
    void 모르는_예외의_키() {
        IllegalArgumentException unknown = new IllegalArgumentException("Illegal character in query at index 120: "
                + "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst?serviceKey=abc%2Bdef%3D%3D&pageNo=1");

        assertThat(KmaForecastProviderImpl.reason(new RuntimeException(unknown)))
                .startsWith("기상청 호출 실패 — java.lang.IllegalArgumentException")
                .contains("serviceKey=***&pageNo=1")
                .doesNotContain("abc%2Bdef");
    }

    @Test
    @DisplayName("연결 실패 문구에 섞인 요청 주소의 인증키를 가림")
    void 키_가리기() {
        String message = "I/O error on GET request for \"https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst"
                + "?serviceKey=abc%2Bdef%3D%3D&pageNo=1\": Connection refused";

        assertThat(KmaForecastProviderImpl.maskKey(message))
                .contains("serviceKey=***&pageNo=1")
                .doesNotContain("abc%2Bdef");
    }
}
