package com.pawtrail.weather.domain.exception;

/**
 * 기상청을 부르지 못했을 때 던집니다.
 *
 * 재시도를 다 썼거나 회로 차단기가 열려 있는 경우입니다.
 * 받는 쪽(서비스)은 직전 발표로 넘기고, 그것도 없으면 WEATHER_UNAVAILABLE 로 답합니다.
 */
public class ForecastUnavailableException extends RuntimeException {

    public ForecastUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
