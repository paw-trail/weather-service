package com.pawtrail.weather.infrastructure.provider.external;

import com.pawtrail.weather.domain.model.ForecastRun;

/**
 * 기상청 응답을 읽은 결과입니다.
 *
 * @param status  읽은 결과의 갈래입니다.
 * @param run     OK 일 때만 있습니다.
 * @param code    기상청 오류 코드입니다. OK 면 00 입니다.
 * @param message 기상청이 준 문구입니다.
 */
public record KmaResponse(Status status, ForecastRun run, String code, String message) {

    public enum Status {
        // 예보를 받음
        OK,
        // 그 발표 자료가 아직 없음 (03) — 실패가 아니라 직전 발표를 쓰라는 뜻
        NO_DATA,
        // 다시 불러 볼 만한 실패 — 기상청 쪽 일시 오류
        RETRYABLE,
        // 다시 불러도 같은 실패 — 인증키 · 파라미터 · 요청 한도
        PERMANENT
    }

    static KmaResponse ok(ForecastRun run) {
        return new KmaResponse(Status.OK, run, "00", "NORMAL_SERVICE");
    }

    static KmaResponse noData(String code, String message) {
        return new KmaResponse(Status.NO_DATA, null, code, message);
    }

    static KmaResponse failure(Status status, String code, String message) {
        return new KmaResponse(status, null, code, message);
    }
}
