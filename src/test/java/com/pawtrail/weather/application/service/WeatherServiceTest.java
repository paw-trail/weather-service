package com.pawtrail.weather.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.weather.application.dto.output.WeatherOutput;
import com.pawtrail.weather.domain.enums.PrecipitationType;
import com.pawtrail.weather.domain.enums.RegionMatch;
import com.pawtrail.weather.domain.enums.SkyCondition;
import com.pawtrail.weather.domain.exception.ForecastUnavailableException;
import com.pawtrail.weather.domain.exception.WeatherErrorCode;
import com.pawtrail.weather.domain.model.ForecastRun;
import com.pawtrail.weather.domain.model.Grid;
import com.pawtrail.weather.domain.model.HourlyForecast;
import com.pawtrail.weather.domain.model.RegionGridRow;
import com.pawtrail.weather.domain.provider.ForecastProvider;
import com.pawtrail.weather.domain.repository.ForecastStore;
import com.pawtrail.weather.domain.rule.RegionGridTable;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 캐시 · 기상청 · 직전 발표로 넘기는 순서를 가짜 저장소와 가짜 기상청으로 봅니다.
 */
class WeatherServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // 14시 20분 — 가장 새 발표는 14시 · 직전은 11시
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 20, 14, 20);
    private static final LocalDateTime BASE = NOW.withHour(14).withMinute(0);
    private static final LocalDateTime PREVIOUS = NOW.withHour(11).withMinute(0);
    private static final Grid SEOUL_GRID = new Grid(60, 127);

    private final FakeStore store = new FakeStore();
    private final FakeProvider provider = new FakeProvider();
    private final RegionGridTable table = new RegionGridTable(List.of(
            new RegionGridRow("11", "서울특별시", null, SEOUL_GRID),
            new RegionGridRow("11", "서울특별시", "종로구", SEOUL_GRID)));
    private final WeatherService service = new WeatherService(provider, store, table,
            Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));

    @Test
    @DisplayName("캐시에 가장 새 발표가 있으면 기상청을 안 부름")
    void 캐시() {
        store.put(run(BASE));

        WeatherOutput out = service.byCoordinates(37.5703777777777, 126.981641666666);

        assertThat(out.baseAt()).isEqualTo(BASE);
        assertThat(out.stale()).isFalse();
        assertThat(out.at()).isEqualTo(BASE.withHour(15));
        assertThat(out.rainToday()).isNotNull();
        assertThat(out.region()).isNull();
        assertThat(provider.calls).isEmpty();
    }

    @Test
    @DisplayName("캐시에 없으면 기상청에서 받아 캐시에 넣음")
    void 기상청() {
        provider.answers.put(BASE, Optional.of(run(BASE)));

        WeatherOutput out = service.byRegion("11", "종로구");

        assertThat(out.stale()).isFalse();
        assertThat(out.region().matched()).isEqualTo(RegionMatch.SIGUNGU);
        assertThat(store.saved).containsKey(BASE);
    }

    @Test
    @DisplayName("발표 자료가 아직 없으면(03) 직전 발표 — 캐시에 없으면 기상청에서")
    void 자료_없음() {
        provider.answers.put(BASE, Optional.empty());
        provider.answers.put(PREVIOUS, Optional.of(run(PREVIOUS)));

        WeatherOutput out = service.byCoordinates(37.57, 126.98);

        assertThat(out.baseAt()).isEqualTo(PREVIOUS);
        assertThat(out.stale()).isTrue();
        assertThat(provider.calls).containsExactly(BASE, PREVIOUS);
    }

    @Test
    @DisplayName("기상청을 못 부르면 캐시의 직전 발표 · 기상청을 다시 부르지 않음")
    void 못_부름_직전() {
        store.put(run(PREVIOUS));
        provider.failing = true;

        WeatherOutput out = service.byCoordinates(37.57, 126.98);

        assertThat(out.baseAt()).isEqualTo(PREVIOUS);
        assertThat(out.stale()).isTrue();
        assertThat(provider.calls).containsExactly(BASE);
    }

    @Test
    @DisplayName("기상청을 못 부르고 직전 발표도 없으면 WEATHER_UNAVAILABLE")
    void 못_부름_없음() {
        provider.failing = true;

        assertThatThrownBy(() -> service.byCoordinates(37.57, 126.98))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(WeatherErrorCode.WEATHER_UNAVAILABLE));
    }

    @Test
    @DisplayName("격자 밖 좌표 · 모르는 시도는 VALIDATION_FAILED")
    void 잘못된_입력() {
        assertThatThrownBy(() -> service.byCoordinates(48.8566, 2.3522))
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(CommonErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> service.byRegion("99", null))
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(CommonErrorCode.VALIDATION_FAILED));
    }

    private static ForecastRun run(LocalDateTime baseAt) {
        List<HourlyForecast> hours = new ArrayList<>();
        for (int h = baseAt.getHour() + 1; h <= 23; h++) {
            PrecipitationType pty = h == 18 ? PrecipitationType.RAIN : PrecipitationType.NONE;
            hours.add(new HourlyForecast(baseAt.withHour(h), 24, SkyCondition.CLEAR, pty, 20));
        }
        return new ForecastRun(SEOUL_GRID, baseAt, hours);
    }

    private static final class FakeStore implements ForecastStore {
        private final Map<LocalDateTime, ForecastRun> runs = new HashMap<>();
        private final Map<LocalDateTime, ForecastRun> saved = new HashMap<>();

        void put(ForecastRun run) {
            runs.put(run.baseAt(), run);
        }

        @Override
        public Optional<ForecastRun> find(Grid grid, LocalDateTime baseAt) {
            return Optional.ofNullable(runs.get(baseAt));
        }

        @Override
        public void save(ForecastRun run) {
            runs.put(run.baseAt(), run);
            saved.put(run.baseAt(), run);
        }
    }

    private static final class FakeProvider implements ForecastProvider {
        private final Map<LocalDateTime, Optional<ForecastRun>> answers = new HashMap<>();
        private final List<LocalDateTime> calls = new ArrayList<>();
        private boolean failing;

        @Override
        public Optional<ForecastRun> fetch(Grid grid, LocalDateTime baseAt) {
            calls.add(baseAt);
            if (failing) {
                throw new ForecastUnavailableException("시험용 실패", null);
            }
            return answers.getOrDefault(baseAt, Optional.empty());
        }
    }
}
