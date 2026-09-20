package com.pawtrail.weather.domain.model;

import com.pawtrail.weather.domain.enums.PrecipitationType;
import java.time.LocalDateTime;

/**
 * 오늘 남은 시간의 첫 비 · 눈 예보입니다.
 *
 * @param firstAt 비나 눈이 예보된 첫 시각입니다.
 * @param type    그 시각의 강수 형태입니다.
 */
public record RainToday(LocalDateTime firstAt, PrecipitationType type) {
}
