package com.pawtrail.weather.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.weather.application.service.WeatherService;
import com.pawtrail.weather.domain.enums.PrecipitationType;
import com.pawtrail.weather.domain.enums.SkyCondition;
import com.pawtrail.weather.domain.model.ForecastRun;
import com.pawtrail.weather.domain.model.Grid;
import com.pawtrail.weather.domain.model.HourlyForecast;
import com.pawtrail.weather.domain.model.RegionGridRow;
import com.pawtrail.weather.domain.repository.ForecastStore;
import com.pawtrail.weather.domain.rule.RegionGridTable;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 두 방식 가운데 하나만 받는 규칙을 컨트롤러를 직접 불러 봅니다 (search 선례 — 서비스 시험은 이 자리를 안 지남).
 */
class WeatherControllerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 20, 14, 20);
    private static final Grid GRID = new Grid(60, 127);

    private final ForecastRun run = new ForecastRun(GRID, NOW.withHour(14).withMinute(0), List.of(
            new HourlyForecast(NOW.withHour(15).withMinute(0), 24, SkyCondition.CLEAR, PrecipitationType.NONE, 10)));

    private final ForecastStore store = new ForecastStore() {
        @Override
        public Optional<ForecastRun> find(Grid grid, LocalDateTime baseAt) {
            return Optional.of(run);
        }

        @Override
        public void save(ForecastRun saved) {
        }
    };

    private final WeatherController controller = new WeatherController(new WeatherService(
            (grid, baseAt) -> Optional.empty(),
            store,
            new RegionGridTable(List.of(new RegionGridRow("11", "서울특별시", null, GRID))),
            Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL)));

    @Test
    @DisplayName("좌표 · 지역 중 하나로 부르면 200")
    void 한_방식() {
        assertThat(controller.weather(37.57, 126.98, null, null).getStatusCode().value()).isEqualTo(200);
        assertThat(controller.weather(null, null, "11", null).getBody().getData().region().sidoCode()).isEqualTo("11");
    }

    @Test
    @DisplayName("둘 다 · 둘 다 없음 · 좌표 하나만 · sigunguName 만이면 VALIDATION_FAILED")
    void 잘못된_조합() {
        assertValidation(() -> controller.weather(37.57, 126.98, "11", null));
        assertValidation(() -> controller.weather(null, null, null, null));
        assertValidation(() -> controller.weather(37.57, null, null, null));
        assertValidation(() -> controller.weather(null, null, null, "종로구"));
        assertValidation(() -> controller.weather(null, null, " ", "종로구"));
    }

    private static void assertValidation(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(CommonErrorCode.VALIDATION_FAILED));
    }
}
