package com.pawtrail.weather.infrastructure.persistence;

import com.pawtrail.weather.domain.enums.PrecipitationType;
import com.pawtrail.weather.domain.enums.SkyCondition;
import com.pawtrail.weather.domain.model.ForecastRun;
import com.pawtrail.weather.domain.model.Grid;
import com.pawtrail.weather.domain.model.HourlyForecast;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 캐시에 넣을 발표 한 번을 글자로 바꾸고 되돌립니다.
 *
 * 모양 — 첫 덩어리가 머리, 나머지가 시각마다 한 덩어리
 *   v1|60|127|202609201400;202609201500|24|MOSTLY_CLOUDY|NONE|30;…
 * 빈 값은 - 로 적습니다. redis-cli 로 열어 봐도 읽히게 JSON 대신 이 모양을 씁니다.
 * 모양을 바꾸면 머리의 v1 을 올리고, 읽을 때 모르는 판이면 캐시에 없는 것으로 봅니다.
 */
public final class ForecastRunCodec {

    static final String VERSION = "v1";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final String NONE = "-";

    private ForecastRunCodec() {
    }

    public static String encode(ForecastRun run) {
        StringBuilder text = new StringBuilder()
                .append(VERSION).append('|')
                .append(run.grid().nx()).append('|')
                .append(run.grid().ny()).append('|')
                .append(run.baseAt().format(TIME));
        for (HourlyForecast hour : run.hours()) {
            text.append(';')
                    .append(hour.at().format(TIME)).append('|')
                    .append(orNone(hour.tmp())).append('|')
                    .append(hour.sky() == null ? NONE : hour.sky().name()).append('|')
                    .append(hour.pty() == null ? NONE : hour.pty().name()).append('|')
                    .append(orNone(hour.pop()));
        }
        return text.toString();
    }

    /**
     * @throws IllegalArgumentException 모양이 다르거나 모르는 판일 때 — 부르는 쪽은 캐시에 없는 것으로 봄
     */
    public static ForecastRun decode(String text) {
        String[] chunks = text.split(";");
        String[] head = chunks[0].split("\\|", -1);
        if (head.length != 4 || !VERSION.equals(head[0])) {
            throw new IllegalArgumentException("모르는 캐시 모양: " + chunks[0]);
        }
        try {
            Grid grid = new Grid(Integer.parseInt(head[1]), Integer.parseInt(head[2]));
            LocalDateTime baseAt = LocalDateTime.parse(head[3], TIME);
            List<HourlyForecast> hours = new ArrayList<>();
            for (int i = 1; i < chunks.length; i++) {
                String[] cells = chunks[i].split("\\|", -1);
                if (cells.length != 5) {
                    throw new IllegalArgumentException("시각 덩어리의 칸 수가 다름: " + chunks[i]);
                }
                hours.add(new HourlyForecast(
                        LocalDateTime.parse(cells[0], TIME),
                        intOrNull(cells[1]),
                        NONE.equals(cells[2]) ? null : SkyCondition.valueOf(cells[2]),
                        NONE.equals(cells[3]) ? null : PrecipitationType.valueOf(cells[3]),
                        intOrNull(cells[4])));
            }
            return new ForecastRun(grid, baseAt, hours);
        } catch (NumberFormatException | DateTimeParseException e) {
            throw new IllegalArgumentException("캐시 값을 읽지 못함", e);
        }
    }

    private static String orNone(Integer value) {
        return value == null ? NONE : value.toString();
    }

    private static Integer intOrNull(String cell) {
        return NONE.equals(cell) ? null : Integer.valueOf(cell);
    }
}
