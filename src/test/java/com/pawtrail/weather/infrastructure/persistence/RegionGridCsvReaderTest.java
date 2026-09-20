package com.pawtrail.weather.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawtrail.weather.domain.enums.RegionMatch;
import com.pawtrail.weather.domain.model.Grid;
import com.pawtrail.weather.domain.rule.RegionGridTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * 레포에 담은 격자 표 파일을 실제로 읽어 봅니다.
 */
class RegionGridCsvReaderTest {

    private final RegionGridTable table = RegionGridCsvReader.read(new ClassPathResource(RegionGridCsvReader.LOCATION));

    @Test
    @DisplayName("시도 16 · 시군구 256 — 2026.7 표에서 광주 · 전남이 하나가 되어 시도가 16")
    void 행_수() {
        assertThat(table.sidoCount()).isEqualTo(16);
        assertThat(table.sigunguCount()).isEqualTo(256);
    }

    @Test
    @DisplayName("실제 표로 찾기 — 서울 종로구 · 경기 고양시 덕양구 · 세종 · 광주 광산구")
    void 실제_표() {
        assertThat(table.resolve("11", "종로구")).hasValueSatisfying(r -> {
            assertThat(r.grid()).isEqualTo(new Grid(60, 127));
            assertThat(r.matched()).isEqualTo(RegionMatch.SIGUNGU);
        });
        assertThat(table.resolve("41", "고양시 덕양구"))
                .hasValueSatisfying(r -> assertThat(r.matched()).isEqualTo(RegionMatch.SIGUNGU));
        assertThat(table.resolve("36", null))
                .hasValueSatisfying(r -> assertThat(r.matched()).isEqualTo(RegionMatch.SIDO));
        assertThat(table.resolve("29", "광산구"))
                .hasValueSatisfying(r -> assertThat(r.matched()).isEqualTo(RegionMatch.SIGUNGU));
    }
}
