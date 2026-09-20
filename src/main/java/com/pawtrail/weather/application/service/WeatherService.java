package com.pawtrail.weather.application.service;

import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.weather.application.dto.output.WeatherOutput;
import com.pawtrail.weather.domain.exception.ForecastUnavailableException;
import com.pawtrail.weather.domain.exception.WeatherErrorCode;
import com.pawtrail.weather.domain.model.ForecastRun;
import com.pawtrail.weather.domain.model.Grid;
import com.pawtrail.weather.domain.model.HourlyForecast;
import com.pawtrail.weather.domain.model.RegionResolution;
import com.pawtrail.weather.domain.provider.ForecastProvider;
import com.pawtrail.weather.domain.repository.ForecastStore;
import com.pawtrail.weather.domain.rule.BaseTimeRule;
import com.pawtrail.weather.domain.rule.ForecastPicker;
import com.pawtrail.weather.domain.rule.GridConverter;
import com.pawtrail.weather.domain.rule.RegionGridTable;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 좌표나 지역으로 지금 날씨를 돌려줍니다.
 *
 * 순서는 늘 같습니다.
 *   가장 새 발표를 캐시에서 찾고, 없으면 기상청에서 받아 캐시에 넣습니다.
 *   기상청에 그 발표 자료가 아직 없으면(03) 직전 발표가 지금 가장 새 예보이므로 그것을 씁니다.
 *   기상청을 못 부르면(재시도를 다 썼거나 회로 차단기가 열림) 캐시에 남은 직전 발표를 씁니다.
 *   둘 다 없으면 WEATHER_UNAVAILABLE 로 답합니다.
 * 직전 발표를 내준 응답은 stale 이 true 이고 baseAt 이 그 발표 시각이라 화면이 밝힐 수 있습니다.
 *
 * 직전 발표로 넘기는 근거는 그것도 글피 너머까지 내다본 예보라 몇 시간 늦었다고 틀린 안내가 되지 않기 때문입니다.
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    private final ForecastProvider forecastProvider;
    private final ForecastStore forecastStore;
    private final RegionGridTable regionGridTable;
    private final Clock clock;

    public WeatherService(ForecastProvider forecastProvider,
                          ForecastStore forecastStore,
                          RegionGridTable regionGridTable,
                          Clock clock) {
        this.forecastProvider = forecastProvider;
        this.forecastStore = forecastStore;
        this.regionGridTable = regionGridTable;
        this.clock = clock;
    }

    /**
     * 좌표의 날씨입니다. 첫 화면(브라우저 위치 · 거부하면 고정 지역)과 장소 상세(장소 좌표)가 부릅니다.
     */
    public WeatherOutput byCoordinates(double lat, double lon) {
        Grid grid = GridConverter.toGrid(lat, lon)
                .orElseThrow(() -> new CustomException(CommonErrorCode.VALIDATION_FAILED));
        return forGrid(grid, null);
    }

    /**
     * 지역의 날씨입니다. 검색 결과가 검색에 쓴 지역 값을 그대로 넘깁니다.
     */
    public WeatherOutput byRegion(String sidoCode, String sigunguName) {
        RegionResolution region = regionGridTable.resolve(sidoCode, sigunguName)
                .orElseThrow(() -> new CustomException(CommonErrorCode.VALIDATION_FAILED));
        return forGrid(region.grid(), region);
    }

    private WeatherOutput forGrid(Grid grid, RegionResolution region) {
        LocalDateTime now = LocalDateTime.now(clock);
        Loaded loaded = load(grid, BaseTimeRule.latestAvailable(now));
        HourlyForecast current = ForecastPicker.current(loaded.run(), now)
                .orElseThrow(() -> {
                    log.warn("예보가 지금 시각을 담고 있지 않음 grid={} baseAt={}", grid, loaded.run().baseAt());
                    return new CustomException(WeatherErrorCode.WEATHER_UNAVAILABLE);
                });
        return WeatherOutput.of(
                loaded.run().baseAt(),
                loaded.stale(),
                current,
                ForecastPicker.rainToday(loaded.run(), now).orElse(null),
                region);
    }

    private Loaded load(Grid grid, LocalDateTime baseAt) {
        Optional<ForecastRun> cached = forecastStore.find(grid, baseAt);
        if (cached.isPresent()) {
            return new Loaded(cached.get(), false);
        }
        LocalDateTime previous = BaseTimeRule.previous(baseAt);
        try {
            Optional<ForecastRun> fetched = forecastProvider.fetch(grid, baseAt);
            if (fetched.isPresent()) {
                forecastStore.save(fetched.get());
                return new Loaded(fetched.get(), false);
            }
            log.info("발표 자료가 아직 없어 직전 발표를 씀 grid={} baseAt={}", grid, baseAt);
            return previousRun(grid, previous);
        } catch (ForecastUnavailableException e) {
            log.warn("기상청을 부르지 못해 캐시의 직전 발표를 찾음 grid={} baseAt={} 까닭={}",
                    grid, baseAt, e.getMessage());
            return forecastStore.find(grid, previous)
                    .map(run -> new Loaded(run, true))
                    .orElseThrow(() -> new CustomException(WeatherErrorCode.WEATHER_UNAVAILABLE, e));
        }
    }

    // 발표 자료가 아직 없을 때 — 캐시에 없으면 기상청에서 직전 발표를 한 번 더 받음
    private Loaded previousRun(Grid grid, LocalDateTime previous) {
        Optional<ForecastRun> cached = forecastStore.find(grid, previous);
        if (cached.isPresent()) {
            return new Loaded(cached.get(), true);
        }
        try {
            Optional<ForecastRun> fetched = forecastProvider.fetch(grid, previous);
            if (fetched.isPresent()) {
                forecastStore.save(fetched.get());
                return new Loaded(fetched.get(), true);
            }
        } catch (ForecastUnavailableException e) {
            throw new CustomException(WeatherErrorCode.WEATHER_UNAVAILABLE, e);
        }
        throw new CustomException(WeatherErrorCode.WEATHER_UNAVAILABLE);
    }

    private record Loaded(ForecastRun run, boolean stale) {
    }
}
