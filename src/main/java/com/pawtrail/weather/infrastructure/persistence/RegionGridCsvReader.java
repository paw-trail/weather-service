package com.pawtrail.weather.infrastructure.persistence;

import com.pawtrail.weather.domain.model.Grid;
import com.pawtrail.weather.domain.model.RegionGridRow;
import com.pawtrail.weather.domain.rule.RegionGridTable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.Resource;

/**
 * 기상청 격자 표 파일을 읽습니다.
 *
 * 파일은 활용가이드의 격자 위경도 엑셀(2607)에서 시도 · 시군구 행만 옮긴 것입니다.
 * # 로 시작하는 줄은 출처 설명이고, 첫 데이터 줄은 칸 이름입니다.
 * 칸 — sido_code, sido_name, sigungu_name(시도 행이면 비움), nx, ny
 */
public final class RegionGridCsvReader {

    public static final String LOCATION = "kma/region-grid.csv";

    private static final String HEADER = "sido_code,sido_name,sigungu_name,nx,ny";

    private RegionGridCsvReader() {
    }

    public static RegionGridTable read(Resource resource) {
        List<RegionGridRow> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean headerSeen = false;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                if (!headerSeen) {
                    if (!line.strip().equals(HEADER)) {
                        throw new IllegalStateException("격자 표의 칸 이름이 다릅니다: " + line);
                    }
                    headerSeen = true;
                    continue;
                }
                rows.add(parse(line, lineNo));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("격자 표를 읽지 못했습니다: " + LOCATION, e);
        }
        if (rows.isEmpty()) {
            throw new IllegalStateException("격자 표가 비어 있습니다: " + LOCATION);
        }
        return new RegionGridTable(rows);
    }

    private static RegionGridRow parse(String line, int lineNo) {
        String[] cells = line.split(",", -1);
        if (cells.length != 5) {
            throw new IllegalStateException("격자 표 " + lineNo + "번째 줄의 칸 수가 다릅니다: " + line);
        }
        try {
            String sigungu = cells[2].isBlank() ? null : cells[2].strip();
            Grid grid = new Grid(Integer.parseInt(cells[3].strip()), Integer.parseInt(cells[4].strip()));
            return new RegionGridRow(cells[0].strip(), cells[1].strip(), sigungu, grid);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("격자 표 " + lineNo + "번째 줄의 격자 번호가 숫자가 아닙니다: " + line, e);
        }
    }
}
