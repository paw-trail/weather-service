package com.pawtrail.weather.domain.model;

/**
 * 기상청 격자 표의 한 행입니다.
 *
 * @param sidoCode    시도 코드입니다. 행정구역코드 앞 두 자리입니다.
 * @param sidoName    시도 이름입니다.
 * @param sigunguName 시군구 이름입니다. 표에 적힌 대로이며 시도 행이면 null 입니다.
 * @param grid        기상청이 그 행정구역에 쓰는 격자입니다.
 */
public record RegionGridRow(String sidoCode, String sidoName, String sigunguName, Grid grid) {

    public boolean isSido() {
        return sigunguName == null;
    }
}
