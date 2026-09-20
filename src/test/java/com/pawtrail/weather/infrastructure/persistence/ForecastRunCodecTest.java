package com.pawtrail.weather.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawtrail.weather.domain.enums.PrecipitationType;
import com.pawtrail.weather.domain.enums.SkyCondition;
import com.pawtrail.weather.domain.model.ForecastRun;
import com.pawtrail.weather.domain.model.Grid;
import com.pawtrail.weather.domain.model.HourlyForecast;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 캐시 글자 모양을 봅니다.
 */
class ForecastRunCodecTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 9, 20, 14, 0);

    @Test
    @DisplayName("글자로 바꿨다 되돌리면 같음 · 빈 칸은 - 로")
    void 되돌리기() {
        ForecastRun run = new ForecastRun(new Grid(60, 127), BASE, List.of(
                new HourlyForecast(BASE.withHour(15), 24, SkyCondition.MOSTLY_CLOUDY, PrecipitationType.NONE, 30),
                new HourlyForecast(BASE.withHour(16), null, null, PrecipitationType.RAIN, null)));

        String text = ForecastRunCodec.encode(run);

        assertThat(text).isEqualTo("v1|60|127|202609201400;202609201500|24|MOSTLY_CLOUDY|NONE|30;202609201600|-|-|RAIN|-");
        assertThat(ForecastRunCodec.decode(text)).isEqualTo(run);
    }

    @Test
    @DisplayName("모르는 판이나 깨진 값은 IllegalArgumentException — 저장소가 없는 것으로 봄")
    void 깨진_값() {
        assertThatThrownBy(() -> ForecastRunCodec.decode("v2|60|127|202609201400"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ForecastRunCodec.decode("v1|60|127|오늘"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
