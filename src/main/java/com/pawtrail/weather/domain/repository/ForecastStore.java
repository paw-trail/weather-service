package com.pawtrail.weather.domain.repository;

import com.pawtrail.weather.domain.model.ForecastRun;
import com.pawtrail.weather.domain.model.Grid;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 받아 온 발표를 잠시 담아 두는 곳의 약속입니다.
 *
 * 격자와 발표 시각이 같으면 예보가 같으므로 그 둘을 열쇠로 씁니다.
 * 구현이 캐시에 닿지 못하면 없는 것으로 답하고 저장은 건너뜁니다 — 캐시가 죽어도 날씨는 나가야 함.
 */
public interface ForecastStore {

    Optional<ForecastRun> find(Grid grid, LocalDateTime baseAt);

    void save(ForecastRun run);
}
