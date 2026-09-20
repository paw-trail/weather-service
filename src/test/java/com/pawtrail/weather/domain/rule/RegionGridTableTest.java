package com.pawtrail.weather.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawtrail.weather.domain.enums.RegionMatch;
import com.pawtrail.weather.domain.model.Grid;
import com.pawtrail.weather.domain.model.RegionGridRow;
import com.pawtrail.weather.domain.model.RegionResolution;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 지역 값을 격자로 바꾸는 순서를 작은 표로 봅니다. 실제 표는 RegionGridCsvReaderTest 가 봅니다.
 */
class RegionGridTableTest {

    private final RegionGridTable table = new RegionGridTable(List.of(
            new RegionGridRow("41", "경기도", null, new Grid(60, 120)),
            new RegionGridRow("41", "경기도", "고양시덕양구", new Grid(57, 128)),
            new RegionGridRow("41", "경기도", "화성시만세구", new Grid(57, 119)),
            new RegionGridRow("41", "경기도", "화성시동탄구", new Grid(61, 120)),
            new RegionGridRow("41", "경기도", "광명시", new Grid(58, 125)),
            new RegionGridRow("12", "전남광주통합특별시", null, new Grid(51, 67)),
            new RegionGridRow("12", "전남광주통합특별시", "광산구", new Grid(57, 74)),
            new RegionGridRow("12", "전남광주통합특별시", "여수시", new Grid(73, 66)),
            new RegionGridRow("28", "인천광역시", null, new Grid(55, 124)),
            new RegionGridRow("28", "인천광역시", "제물포구", new Grid(54, 125))));

    @Test
    @DisplayName("시군구 이름이 없으면 시도 대표")
    void 시도만() {
        assertThat(table.resolve("41", null)).contains(new RegionResolution("41", null, RegionMatch.SIDO, new Grid(60, 120)));
        assertThat(table.resolve("41", " ")).contains(new RegionResolution("41", null, RegionMatch.SIDO, new Grid(60, 120)));
    }

    @Test
    @DisplayName("띄어쓰기를 빼고 같은 이름 — 우리 고양시 덕양구 · 표 고양시덕양구")
    void 같은_이름() {
        assertThat(grid("41", "고양시 덕양구")).isEqualTo(new Grid(57, 128));
        assertThat(match("41", "고양시 덕양구")).isEqualTo(RegionMatch.SIGUNGU);
    }

    @Test
    @DisplayName("우리 이름이 시까지만이면 표 순서의 첫 구 · 우리 이름이 시와 구면 표의 시")
    void 앞부분() {
        assertThat(grid("41", "화성시")).isEqualTo(new Grid(57, 119));
        assertThat(grid("41", "광명시 철산구")).isEqualTo(new Grid(58, 125));
    }

    @Test
    @DisplayName("광주 29 · 전남 46 은 표의 12 전남광주통합특별시로")
    void 광주_전남() {
        assertThat(grid("29", "광산구")).isEqualTo(new Grid(57, 74));
        assertThat(grid("46", "여수시")).isEqualTo(new Grid(73, 66));
        assertThat(table.resolve("29", "광산구")).hasValueSatisfying(r -> assertThat(r.sidoCode()).isEqualTo("29"));
    }

    @Test
    @DisplayName("인천 옛 동구는 제물포구 · 옛 중구는 갈려서 시도 대표")
    void 인천_개편() {
        assertThat(grid("28", "동구")).isEqualTo(new Grid(54, 125));
        assertThat(match("28", "중구")).isEqualTo(RegionMatch.SIDO);
        assertThat(grid("28", "중구")).isEqualTo(new Grid(55, 124));
    }

    @Test
    @DisplayName("모르는 시도 코드면 빈 값")
    void 모르는_시도() {
        assertThat(table.resolve("99", "어디구")).isEmpty();
        assertThat(table.resolve(null, "어디구")).isEmpty();
    }

    private Grid grid(String sido, String sigungu) {
        return table.resolve(sido, sigungu).orElseThrow().grid();
    }

    private RegionMatch match(String sido, String sigungu) {
        return table.resolve(sido, sigungu).orElseThrow().matched();
    }
}
