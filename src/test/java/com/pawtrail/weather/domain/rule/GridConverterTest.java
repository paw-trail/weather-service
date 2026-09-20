package com.pawtrail.weather.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawtrail.weather.domain.model.Grid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 좌표를 기상청 격자로 바꾸는 식을 봅니다. 기대값은 활용가이드 격자 표(2607)의 행 그대로입니다.
 */
class GridConverterTest {

    @Test
    @DisplayName("격자 표의 시도 · 시군구 좌표가 표의 격자와 같은 칸으로 바뀜")
    void 표의_좌표() {
        assertThat(GridConverter.toGrid(37.5635694444444, 126.980008333333)).contains(new Grid(60, 127));
        assertThat(GridConverter.toGrid(37.5703777777777, 126.981641666666)).contains(new Grid(60, 127));
        assertThat(GridConverter.toGrid(35.1770194444444, 129.076952777777)).contains(new Grid(98, 76));
        assertThat(GridConverter.toGrid(33.4856944444444, 126.500333333333)).contains(new Grid(52, 38));
    }

    @Test
    @DisplayName("우리나라 격자 밖이거나 숫자가 아니면 빈 값")
    void 격자_밖() {
        assertThat(GridConverter.toGrid(0, 0)).isEmpty();
        assertThat(GridConverter.toGrid(48.8566, 2.3522)).isEmpty();
        assertThat(GridConverter.toGrid(Double.NaN, 126.98)).isEmpty();
        assertThat(GridConverter.toGrid(91, 126.98)).isEmpty();
    }
}
