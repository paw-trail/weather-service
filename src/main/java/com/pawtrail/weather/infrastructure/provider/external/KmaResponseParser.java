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
 * 응답은 세 모양으로 옵니다.
 *   JSON 기상청    response.header.resultCode · response.body.items.item[] — 정상과 기상청 자체 오류
 *   JSON 게이트웨이  OpenAPI_ServiceResponse.cmmMsgHeader.returnReasonCode — 공공데이터포털이 막은 경우
 *                  (인증키 · 한도) · 틀린 키로 불렀을 때 HTTP 403 과 함께 이 모양으로 왔음 (2026.9.20 실물)
 *   XML           같은 게이트웨이 봉투가 XML 로 오는 경우 — dataType 을 못 읽기 전에 막히면 이 모양임
 * 오류 코드는 활용가이드 「Open API 에러 코드 정리」 를 따릅니다.
 *
 * 어느 모양도 아니면 본문 앞부분을 문구에 담아 돌려줍니다. 까닭 없이 "실패" 만 남으면 로그로 원인을 찾을 수 없기 때문입니다.
 */
public final class KmaResponseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmm");

    static final String OK = "00";
    static final String NO_DATA = "03";

    // 다시 불러 볼 오류 코드 — 서비스 연결 실패(05) 하나 (이슈 #1 · ingest 선례)
    // 인증키 · 파라미터 · 요청 한도(10 · 11 · 12 · 20 · 21 · 22 · 30 · 31 · 32 · 33)는 다시 불러도 같고
    // 요청 한도(22)는 다시 부르면 그만큼 한도만 씀
    // 어플리케이션 · DB · HTTP · 기타(01 · 02 · 04 · 99)는 되풀이해 나아진다는 근거가 없어
    // 요청을 받은 자리에서 기다리게만 하므로 바로 직전 발표로 넘김
    static final Set<String> RETRYABLE_CODES = Set.of("05");

    // 기상청이 값을 비워 둘 때 넣는 자리표 — +900 이상 · -900 이하 (해상 격자의 마스킹 등)
    private static final double MISSING_LIMIT = 900;

    private static final Pattern XML_CODE = Pattern.compile(
            "<(?:returnReasonCode|resultCode)>\\s*(\\d+)\\s*</(?:returnReasonCode|resultCode)>");
    private static final Pattern XML_MESSAGE = Pattern.compile(
            "<(errMsg|returnAuthMsg|resultMsg)>\\s*([^<]*?)\\s*</\\1>");

    // 알 수 없는 응답을 문구에 담을 때 자르는 길이
    private static final int SNIPPET_LENGTH = 200;

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
            return KmaResponse.failure(Status.RETRYABLE, "UNREADABLE", "응답을 읽지 못함: " + snippet(text));
        }
        JsonNode gateway = root.path("OpenAPI_ServiceResponse").path("cmmMsgHeader");
        if (!gateway.isMissingNode()) {
            return classify(normalizeCode(gateway.path("returnReasonCode").asText("")),
                    join(gateway.path("errMsg").asText(""), gateway.path("returnAuthMsg").asText("")));
        }
        JsonNode header = root.path("response").path("header");
        if (header.isMissingNode()) {
            return KmaResponse.failure(Status.PERMANENT, "UNKNOWN", "모르는 응답 모양: " + snippet(text));
        }
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
        if (!code.find()) {
            return KmaResponse.failure(Status.PERMANENT, "UNKNOWN", "모르는 응답 모양: " + snippet(text));
        }
        String found = normalizeCode(code.group(1));
        if (OK.equals(found)) {
            // XML 로 온 정상 응답은 받지 않음 — 늘 dataType=JSON 으로 부르므로 오면 이상한 것
            return KmaResponse.failure(Status.RETRYABLE, found, "XML 로 온 정상 응답");
        }
        Matcher message = XML_MESSAGE.matcher(text);
        String said = "";
        while (message.find()) {
            said = join(said, message.group(2));
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

    // 비지 않은 문구만 · 로 이음 — 게이트웨이는 코드 이름과 한글 설명을 두 칸에 나눠 줌
    private static String join(String first, String second) {
        String a = first == null ? "" : first.strip();
        String b = second == null ? "" : second.strip();
        if (a.isEmpty() || a.equals(b)) {
            return b;
        }
        return b.isEmpty() ? a : a + " · " + b;
    }

    // 로그에 담을 본문 앞부분 — 줄바꿈 · 공백을 한 칸으로 줄이고 자름
    private static String snippet(String text) {
        String flat = text.replaceAll("\\s+", " ").strip();
        return flat.length() <= SNIPPET_LENGTH ? flat : flat.substring(0, SNIPPET_LENGTH) + "…";
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
