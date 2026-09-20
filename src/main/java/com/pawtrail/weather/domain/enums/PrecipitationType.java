package com.pawtrail.weather.domain.enums;

/**
 * 강수 형태입니다.
 *
 * 기상청 단기예보의 PTY 코드를 이름으로 바꾼 것입니다.
 * 단기예보의 코드는 없음 0 · 비 1 · 비/눈 2 · 눈 3 · 소나기 4 입니다.
 * 빗방울 5 · 빗방울눈날림 6 · 눈날림 7 은 초단기 조회에만 있어 이 서비스가 받지 않습니다.
 */
public enum PrecipitationType {

    NONE(0),
    RAIN(1),
    RAIN_SNOW(2),
    SNOW(3),
    SHOWER(4);

    private final int code;

    PrecipitationType(int code) {
        this.code = code;
    }

    /**
     * 비나 눈이 오는 예보인지 봅니다. 장소 상세의 비 예보 경고가 이 값을 씁니다.
     */
    public boolean isPrecipitation() {
        return this != NONE;
    }

    /**
     * 기상청 코드를 이름으로 바꿉니다. 모르는 코드면 null 을 돌려줍니다.
     */
    public static PrecipitationType fromCode(int code) {
        for (PrecipitationType value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }
}
