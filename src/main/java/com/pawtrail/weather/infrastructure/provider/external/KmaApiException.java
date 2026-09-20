package com.pawtrail.weather.infrastructure.provider.external;

/**
 * 기상청 호출이 실패했을 때 던집니다.
 *
 * retryable 이 false 면 다시 불러도 결과가 같은 실패입니다 — 인증키 · 파라미터 · 요청 한도.
 * 이 예외는 회로 차단기를 지나 ForecastUnavailableException 으로 바뀌어 서비스에 닿습니다.
 */
public class KmaApiException extends RuntimeException {

    private final String code;
    private final boolean retryable;

    public KmaApiException(String code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    public KmaApiException(String code, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.retryable = retryable;
    }

    public String getCode() {
        return code;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
