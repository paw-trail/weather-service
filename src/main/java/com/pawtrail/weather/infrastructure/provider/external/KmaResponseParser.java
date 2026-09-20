package com.pawtrail.weather.infrastructure.provider.external;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawtrail.weather.domain.enums.PrecipitationType;
import com.pawtrail.weather.domain.enums.SkyCondition;
import com.pawtrail.weather.domain.model.ForecastRun;
import com.pawtrail.weather.domain.model.Grid;
import com.pawtrail.weather.domain.model.HourlyForecast;
import com.pawtrail.weather.infrastructure.provider.external.KmaResponse.Status;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 기상청 단기예보 응답을 읽습니다. 네트워크를 모르는 순수한 읽기라 응답 글자만으로 시험합니다.
 *
 * 응답은 두 모양으로 옵니다.
 *   JSON   response.header.resultCode · response.body.items.item[] — 정상과 기상청 자체 오류
 *   XML    공공데이터포털 게이트웨이가 막은 경우 (인증키 · 한도) — dataType=JSON 이어도 XML 로 옴
 * 오류 코드는 활용가이드 「Open API 에러 코드 정리」 를 따릅니다.
 */
public final class KmaResponseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmm");

    static final String OK = "00";
    static final String NO_DATA = "03";

    // 기상청 쪽 일시 오류 — 어플리케이션 · DB · HTTP · 서비스 연결 실패 · 기타
    // 인증키 · 파라미터 · 요청 한도(10 · 11 · 12 · 20 · 21 · 22 · 30 · 31 · 32 · 33)는 다시 불러도 같아 넣지 않음
    // 요청 한도(22)를 다시 부르면 그만큼 한도만 씀 (ingest 선례)
    static final Set<String> RETRYABLE_CODES = Set.of("01", "02", "04", "05", "99");

    // 기상청이 값을 비워 둘 때 넣는 자리표 — +900 이상 · -900 이하 (해상 격자의 마스킹 등)
    private static final double MISSING_LIMIT = 900;

    private static final Pattern XML_CODE = Pattern.compile(
            "<(?:returnReasonCode|resultCode)>\\s*(\\d+)\\s*</(?:returnReasonCode|resultCode)>");
    private static final Pattern XML_MESSAGE = Pattern.compile(
            "<(?:returnAuthMsg|resultMsg)>\\s*([^<]*?)\\s*</(?:returnAuthMsg|resultMsg)>");

    private KmaResponseParser() {
    }

    public static KmaResponse parse(String body, Grid grid, LocalDateTime baseAt) {
        if (body == null || body.isBlank()) {
            return KmaResponse.failure(Status.RETRYABLE, "EMPTY", "빈 응답");
        }
        String text = body.strip();
        if (text.startsWith("<")) {
            return parseXml(text);
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(text);
        } catch (JsonProcessingException e) {
            return KmaResponse.failure(Status.RETRYABLE, "UNREADABLE", "응답을 읽지 못함");
        }
        JsonNode header = root.path("response").path("header");
        String code = normalizeCode(header.path("resultCode").asText(""));
        String message = header.path("resultMsg").asText("");
        if (!OK.equals(code)) {
            return classify(code, message);
        }
        List<HourlyForecast> hours = hours(root.path("response").path("body").path("items").path("item"), grid, baseAt);
        if (hours.isEmpty()) {
            return KmaResponse.noData(code, "정상 응답이지만 예보 행이 없음");
        }
        return KmaResponse.ok(new ForecastRun(grid, baseAt, hours));
    }

    // 요청과 다른 격자 · 발표의 행은 버림 — 섞여 오면 다른 곳의 예보를 내게 됨
    private static List<HourlyForecast> hours(JsonNode items, Grid grid, LocalDateTime baseAt) {
        String baseDate = baseAt.format(DATE);
        String baseTime = baseAt.format(TIME);
        Map<LocalDateTime, Map<String, String>> byTime = new LinkedHashMap<>();
        for (JsonNode item : items) {
            if (!baseDate.equals(item.path("baseDate").asText())
                    || !baseTime.equals(item.path("baseTime").asText())
                    || grid.nx() != item.path("nx").asInt(-1)
                    || grid.ny() != item.path("ny").asInt(-1)) {
                continue;
            }
            LocalDateTime at;
            try {
                at = LocalDateTime.parse(item.path("fcstDate").asText() + item.path("fcstTime").asText(), DATE_TIME);
            } catch (DateTimeParseException e) {
                continue;
            }
            byTime.computeIfAbsent(at, k -> new LinkedHashMap<>())
                    .put(item.path("category").asText(), item.path("fcstValue").asText());
        }
        List<HourlyForecast> hours = new ArrayList<>();
        for (Map.Entry<LocalDateTime, Map<String, String>> entry : byTime.entrySet()) {
            Map<String, String> values = entry.getValue();
            Integer tmp = number(values.get("TMP"));
            Integer sky = number(values.get("SKY"));
            Integer pty = number(values.get("PTY"));
            Integer pop = number(values.get("POP"));
            if (tmp == null && sky == null && pty == null && pop == null) {
                continue;
            }
            hours.add(new HourlyForecast(
                    entry.getKey(),
                    tmp,
                    sky == null ? null : SkyCondition.fromCode(sky),
                    pty == null ? null : PrecipitationType.fromCode(pty),
                    pop));
        }
        return hours;
    }

    private static KmaResponse parseXml(String text) {
        Matcher code = XML_CODE.matcher(text);
        Matcher message = XML_MESSAGE.matcher(text);
        String found = code.find() ? normalizeCode(code.group(1)) : "XML";
        String said = message.find() ? message.group(1) : "게이트웨이 오류";
        if (OK.equals(found)) {
            // XML 로 온 정상 응답은 받지 않음 — 늘 dataType=JSON 으로 부르므로 오면 이상한 것
            return KmaResponse.failure(Status.RETRYABLE, found, "XML 로 온 정상 응답");
        }
        return classify(found, said);
    }

    private static KmaResponse classify(String code, String message) {
        if (NO_DATA.equals(code)) {
            return KmaResponse.noData(code, message);
        }
        Status status = RETRYABLE_CODES.contains(code) ? Status.RETRYABLE : Status.PERMANENT;
        return KmaResponse.failure(status, code, message);
    }

    // "0" · "3" 처럼 한 자리로 오는 경우를 두 자리로 맞춤 (가이드 XML 예시가 0 으로 적혀 있음)
    private static String normalizeCode(String code) {
        String trimmed = code.strip();
        return trimmed.length() == 1 ? "0" + trimmed : trimmed;
    }

    private static Integer number(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(value.strip());
            if (parsed >= MISSING_LIMIT || parsed <= -MISSING_LIMIT) {
                return null;
            }
            return (int) Math.round(parsed);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
