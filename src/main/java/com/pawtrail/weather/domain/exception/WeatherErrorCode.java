package com.pawtrail.weather.domain.exception;

import com.pawtrail.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 날씨 서비스의 에러 코드입니다. 상수 이름이 곧 응답의 code 입니다.
 */
public enum WeatherErrorCode implements ErrorCode {

    // 새 발표도 직전 발표도 없음 — 기상청을 못 부르고 캐시에도 없음
    // 화면은 날씨 자리를 숨김 · 없음과 고장을 섞지 않으려고 200 + null 로 답하지 않음
    WEATHER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "날씨를 불러오지 못했습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    WeatherErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return this.httpStatus;
    }

    @Override
    public String getCode() {
        return this.name();
    }

    @Override
    public String getMessage() {
        return this.message;
    }
}
