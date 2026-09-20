package com.pawtrail.weather.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 지금 받을 수 있는 가장 새 발표 시각을 봅니다. 발표는 세 시간마다 · 자료는 10분 뒤부터입니다.
 */
class BaseTimeRuleTest {

    @Test
    @DisplayName("발표 10분 전까지는 그 앞 발표 · 10분부터는 그 발표")
    void 십분_경계() {
        assertThat(BaseTimeRule.latestAvailable(at(14, 9))).isEqualTo(at(11, 0));
        assertThat(BaseTimeRule.latestAvailable(at(14, 10))).isEqualTo(at(14, 0));
        assertThat(BaseTimeRule.latestAvailable(at(23, 59))).isEqualTo(at(23, 0));
    }

    @Test
    @DisplayName("자정부터 02시 10분 전까지는 전날 23시 발표")
    void 자정_넘김() {
        assertThat(BaseTimeRule.latestAvailable(at(0, 30))).isEqualTo(at(23, 0).minusDays(1));
        assertThat(BaseTimeRule.latestAvailable(at(2, 9))).isEqualTo(at(23, 0).minusDays(1));
        assertThat(BaseTimeRule.latestAvailable(at(2, 10))).isEqualTo(at(2, 0));
    }

    @Test
    @DisplayName("직전 발표는 세 시간 앞 · 02시의 앞은 전날 23시")
    void 직전_발표() {
        assertThat(BaseTimeRule.previous(at(14, 0))).isEqualTo(at(11, 0));
        assertThat(BaseTimeRule.previous(at(2, 0))).isEqualTo(at(23, 0).minusDays(1));
    }

    private static LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2026, 9, 20, hour, minute);
    }
}
