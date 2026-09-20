package com.pawtrail.weather.domain.enums;

/**
 * 하늘 상태입니다.
 *
 * 기상청 단기예보의 SKY 코드를 이름으로 바꾼 것이며 화면은 이 이름으로 아이콘을 고릅니다.
 * 코드는 맑음 1 · 구름많음 3 · 흐림 4 셋뿐입니다 (활용가이드 「특정 요소의 코드값 및 범주」).
 */
public enum SkyCondition {

    CLEAR(1),
    MOSTLY_CLOUDY(3),
    CLOUDY(4);

    private final int code;

    SkyCondition(int code) {
        this.code = code;
    }

    /**
     * 기상청 코드를 이름으로 바꿉니다.
     *
     * 모르는 코드면 null 을 돌려줍니다. 코드가 늘어도 응답 전체를 실패시키지 않고 그 칸만 비웁니다.
     */
    public static SkyCondition fromCode(int code) {
        for (SkyCondition value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }
}
