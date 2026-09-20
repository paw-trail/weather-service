package com.pawtrail.weather.domain.enums;

/**
 * 지역으로 부른 날씨가 어느 단계에서 격자를 찾았는지입니다.
 *
 * SIGUNGU 면 그 시군구의 격자이고, SIDO 면 시군구를 찾지 못했거나 보내지 않아 시도 대표 격자를 쓴 것입니다.
 * 화면은 이 값으로 "종로구 날씨" 와 "서울 날씨" 를 가려 씁니다.
 */
public enum RegionMatch {

    SIGUNGU,
    SIDO
}
