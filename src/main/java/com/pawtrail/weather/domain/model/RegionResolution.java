package com.pawtrail.weather.domain.model;

import com.pawtrail.weather.domain.enums.RegionMatch;

/**
 * 지역을 격자로 바꾼 결과입니다.
 *
 * @param sidoCode    요청한 시도 코드입니다. 그대로 돌려줍니다.
 * @param sigunguName 요청한 시군구 이름입니다. 그대로 돌려주며 보내지 않았으면 null 입니다.
 * @param matched     어느 단계에서 찾았는지입니다.
 * @param grid        찾은 격자입니다.
 */
public record RegionResolution(String sidoCode, String sigunguName, RegionMatch matched, Grid grid) {
}
