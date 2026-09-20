package com.pawtrail.weather.domain.model;

import com.pawtrail.weather.domain.enums.PrecipitationType;
import com.pawtrail.weather.domain.enums.SkyCondition;
import java.time.LocalDateTime;

/**
 * 한 시각의 예보입니다.
 *
 * @param at  예보 시각입니다. 정시 단위입니다.
 * @param tmp 기온(℃)입니다. 기상청이 값을 비워 두면 null 입니다.
 * @param sky 하늘 상태입니다. 모르는 코드면 null 입니다.
 * @param pty 강수 형태입니다. 모르는 코드면 null 입니다.
 * @param pop 강수 확률(%)입니다. 기상청이 값을 비워 두면 null 입니다.
 */
public record HourlyForecast(LocalDateTime at,
                             Integer tmp,
                             SkyCondition sky,
                             PrecipitationType pty,
                             Integer pop) {
}
