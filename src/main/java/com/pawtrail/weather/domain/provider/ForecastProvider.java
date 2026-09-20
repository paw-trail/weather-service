package com.pawtrail.weather.domain.provider;

import com.pawtrail.weather.domain.exception.ForecastUnavailableException;
import com.pawtrail.weather.domain.model.ForecastRun;
import com.pawtrail.weather.domain.model.Grid;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 기상청 단기예보를 받아 오는 약속입니다.
 *
 * 구현은 infrastructure 에 있고 재시도 · 회로 차단기를 그 안에서 겁니다.
 */
public interface ForecastProvider {

    /**
     * 한 격자의 한 발표를 받아 옵니다.
     *
     * @return 그 발표 자료가 아직 없으면 빈 값 (기상청 오류 코드 03)
     * @throws ForecastUnavailableException 기상청을 부르지 못했음 — 재시도를 다 썼거나 회로 차단기가 열려 있음
     */
    Optional<ForecastRun> fetch(Grid grid, LocalDateTime baseAt);
}
