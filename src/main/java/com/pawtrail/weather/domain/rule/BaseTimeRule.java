package com.pawtrail.weather.domain.rule;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 지금 부를 수 있는 가장 새 발표 시각을 셉니다.
 *
 * 단기예보는 02 · 05 · 08 · 11 · 14 · 17 · 20 · 23시에 발표되고
 * 각 발표는 10분 뒤부터 받을 수 있습니다 (활용가이드 「예보 발표시각」).
 * 그래서 14시 9분에는 11시 발표가, 14시 10분부터는 14시 발표가 가장 새것입니다.
 */
public final class BaseTimeRule {

    private static final int[] BASE_HOURS = {2, 5, 8, 11, 14, 17, 20, 23};

    // 발표 시각부터 자료가 나올 때까지 (가이드 「API 제공 시간(~이후)」)
    public static final Duration AVAILABLE_AFTER = Duration.ofMinutes(10);

    // 발표 간격 — 직전 발표는 이만큼 앞
    public static final Duration INTERVAL = Duration.ofHours(3);

    private BaseTimeRule() {
    }

    /**
     * 지금 받을 수 있는 가장 새 발표 시각을 돌려줍니다.
     *
     * 자정부터 02시 10분 전까지는 전날 23시 발표입니다.
     */
    public static LocalDateTime latestAvailable(LocalDateTime now) {
        LocalDateTime shifted = now.minus(AVAILABLE_AFTER);
        LocalDate date = shifted.toLocalDate();
        int hour = shifted.getHour();
        for (int i = BASE_HOURS.length - 1; i >= 0; i--) {
            if (hour >= BASE_HOURS[i]) {
                return date.atTime(BASE_HOURS[i], 0);
            }
        }
        return date.minusDays(1).atTime(23, 0);
    }

    /**
     * 그 발표 바로 앞의 발표 시각입니다. 02시의 앞은 전날 23시입니다.
     */
    public static LocalDateTime previous(LocalDateTime baseAt) {
        return baseAt.minus(INTERVAL);
    }
}
