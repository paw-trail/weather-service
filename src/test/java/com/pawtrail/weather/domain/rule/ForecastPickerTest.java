package com.pawtrail.weather.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawtrail.weather.domain.enums.PrecipitationType;
import com.pawtrail.weather.domain.enums.SkyCondition;
import com.pawtrail.weather.domain.model.ForecastRun;
import com.pawtrail.weather.domain.model.Grid;
import com.pawtrail.weather.domain.model.HourlyForecast;
import com.pawtrail.weather.domain.model.RainToday;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 발표 한 번에서 지금 줄과 오늘 비 · 눈 예보를 고르는 것을 봅니다.
 */
class ForecastPickerTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 9, 20, 14, 0);

    private final ForecastRun run = new ForecastRun(new Grid(60, 127), BASE, List.of(
            hour(15, PrecipitationType.NONE),
            hour(16, PrecipitationType.NONE),
            hour(18, PrecipitationType.RAIN),
            new HourlyForecast(LocalDateTime.of(2026, 9, 21, 9, 0), 18, SkyCondition.CLOUDY, PrecipitationType.SNOW, 80)));

    @Test
    @DisplayName("지금 시각을 정시로 내린 것과 같거나 뒤인 첫 줄 — 14시 발표는 15시부터")
    void 지금_줄() {
        assertThat(ForecastPicker.current(run, LocalDateTime.of(2026, 9, 20, 14, 20)))
                .hasValueSatisfying(h -> assertThat(h.at()).isEqualTo(BASE.withHour(15)));
        assertThat(ForecastPicker.current(run, LocalDateTime.of(2026, 9, 20, 16, 40)))
                .hasValueSatisfying(h -> assertThat(h.at()).isEqualTo(BASE.withHour(16)));
    }

    @Test
    @DisplayName("오늘 남은 시간의 첫 비 · 눈 예보 · 내일 것은 세지 않음")
    void 오늘_비() {
        assertThat(ForecastPicker.rainToday(run, LocalDateTime.of(2026, 9, 20, 14, 20)))
                .contains(new RainToday(BASE.withHour(18), PrecipitationType.RAIN));
        assertThat(ForecastPicker.rainToday(run, LocalDateTime.of(2026, 9, 20, 19, 0))).isEmpty();
    }

    private static HourlyForecast hour(int hour, PrecipitationType pty) {
        return new HourlyForecast(BASE.withHour(hour), 24, SkyCondition.MOSTLY_CLOUDY, pty, 30);
    }
}
