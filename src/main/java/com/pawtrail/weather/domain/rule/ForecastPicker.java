package com.pawtrail.weather.domain.rule;

import com.pawtrail.weather.domain.model.ForecastRun;
import com.pawtrail.weather.domain.model.HourlyForecast;
import com.pawtrail.weather.domain.model.RainToday;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * 발표 한 번에서 화면에 보일 줄을 고릅니다.
 */
public final class ForecastPicker {

    private ForecastPicker() {
    }

    /**
     * 지금에 가장 가까운 예보 한 줄입니다.
     *
     * 지금 시각을 정시로 내린 것과 같거나 뒤인 첫 줄을 고릅니다.
     * 14시 발표는 15시부터 담으므로 14시 20분에 부르면 15시 줄이 나갑니다.
     * 그 시각을 응답의 at 에 실어 화면이 밝히게 합니다.
     */
    public static Optional<HourlyForecast> current(ForecastRun run, LocalDateTime now) {
        LocalDateTime hour = now.truncatedTo(ChronoUnit.HOURS);
        return run.hours().stream()
                .filter(h -> !h.at().isBefore(hour))
                .findFirst();
    }

    /**
     * 오늘 남은 시간의 첫 비 · 눈 예보입니다.
     *
     * 강수 확률 몇 % 부터 비로 볼지는 정할 근거가 없어 쓰지 않고,
     * 기상청이 강수 형태를 비 · 눈으로 예보한 시각만 봅니다. 내일로 넘어간 예보는 세지 않습니다.
     */
    public static Optional<RainToday> rainToday(ForecastRun run, LocalDateTime now) {
        LocalDateTime hour = now.truncatedTo(ChronoUnit.HOURS);
        LocalDate today = now.toLocalDate();
        return run.hours().stream()
                .filter(h -> !h.at().isBefore(hour))
                .filter(h -> h.at().toLocalDate().equals(today))
                .filter(h -> h.pty() != null && h.pty().isPrecipitation())
                .findFirst()
                .map(h -> new RainToday(h.at(), h.pty()));
    }
}
