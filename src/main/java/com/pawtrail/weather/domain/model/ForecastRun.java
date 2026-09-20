package com.pawtrail.weather.domain.model;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 한 격자의 한 발표입니다.
 *
 * 발표 한 번이 앞으로 며칠의 시각별 예보를 담습니다.
 * 캐시에는 이 단위로 넣고, 요청마다 지금 시각에 맞는 줄을 여기서 고릅니다.
 *
 * @param grid   격자입니다.
 * @param baseAt 발표 시각입니다. 02 · 05 · 08 · 11 · 14 · 17 · 20 · 23시 가운데 하나입니다.
 * @param hours  시각별 예보입니다. 만들 때 이른 시각부터 늘어놓습니다.
 */
public record ForecastRun(Grid grid, LocalDateTime baseAt, List<HourlyForecast> hours) {

    public ForecastRun {
        hours = hours.stream()
                .sorted(Comparator.comparing(HourlyForecast::at))
                .toList();
    }
}
