package com.pawtrail.weather.infrastructure.provider.external;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawtrail.weather.domain.enums.PrecipitationType;
import com.pawtrail.weather.domain.enums.SkyCondition;
import com.pawtrail.weather.domain.model.Grid;
import com.pawtrail.weather.domain.model.HourlyForecast;
import com.pawtrail.weather.infrastructure.provider.external.KmaResponse.Status;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 기상청 응답 읽기를 봅니다. 모양은 활용가이드의 응답 명세 · 오류 코드를 따릅니다.
 */
class KmaResponseParserTest {

    private static final Grid GRID = new Grid(60, 127);
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 9, 20, 14, 0);

    @Test
    @DisplayName("정상 응답 — 시각마다 기온 · 하늘 · 강수 형태 · 강수 확률을 모음")
    void 정상() {
        String body = ok(item("1500", "TMP", "24") + "," + item("1500", "SKY", "3") + ","
                + item("1500", "PTY", "0") + "," + item("1500", "POP", "30") + ","
                + item("1500", "UUU", "1.2") + "," + item("1600", "TMP", "23") + ","
                + item("1600", "PTY", "1"));

        KmaResponse response = KmaResponseParser.parse(body, GRID, BASE);

        assertThat(response.status()).isEqualTo(Status.OK);
        assertThat(response.run().hours()).containsExactly(
                new HourlyForecast(BASE.withHour(15), 24, SkyCondition.MOSTLY_CLOUDY, PrecipitationType.NONE, 30),
                new HourlyForecast(BASE.withHour(16), 23, null, PrecipitationType.RAIN, null));
    }

    @Test
    @DisplayName("요청과 다른 격자의 행 · 빈 값 자리표(+900 이상 · -900 이하)는 버림")
    void 다른_격자와_빈_값() {
        String otherGrid = "{\"baseDate\":\"20260920\",\"baseTime\":\"1400\",\"category\":\"TMP\","
                + "\"fcstDate\":\"20260920\",\"fcstTime\":\"1500\",\"fcstValue\":\"30\",\"nx\":61,\"ny\":127}";
        String body = ok(item("1500", "TMP", "-999") + "," + item("1500", "POP", "20") + "," + otherGrid);

        KmaResponse response = KmaResponseParser.parse(body, GRID, BASE);

        assertThat(response.run().hours()).containsExactly(
                new HourlyForecast(BASE.withHour(15), null, null, null, 20));
    }

    @Test
    @DisplayName("03 자료 없음은 실패가 아니라 NO_DATA · 한 자리로 온 코드도 두 자리로 맞춤")
    void 자료_없음() {
        assertThat(KmaResponseParser.parse(header("03", "NO_DATA"), GRID, BASE).status()).isEqualTo(Status.NO_DATA);
        assertThat(KmaResponseParser.parse(header("3", "NO_DATA"), GRID, BASE).status()).isEqualTo(Status.NO_DATA);
    }

    @Test
    @DisplayName("기상청 일시 오류는 다시 시도 · 인증키 · 한도는 다시 안 함")
    void 오류_가르기() {
        assertThat(KmaResponseParser.parse(header("05", "SERVICETIME_OUT"), GRID, BASE).status()).isEqualTo(Status.RETRYABLE);
        assertThat(KmaResponseParser.parse(header("99", "UNKNOWN_ERROR"), GRID, BASE).status()).isEqualTo(Status.RETRYABLE);
        assertThat(KmaResponseParser.parse(header("22", "LIMITED"), GRID, BASE).status()).isEqualTo(Status.PERMANENT);
        assertThat(KmaResponseParser.parse(header("10", "INVALID"), GRID, BASE).status()).isEqualTo(Status.PERMANENT);
    }

    @Test
    @DisplayName("게이트웨이가 XML 로 막은 응답 — 미등록 키 30 은 다시 안 함")
    void 게이트웨이_XML() {
        String xml = "<OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg>"
                + "<returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg>"
                + "<returnReasonCode>30</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>";

        KmaResponse response = KmaResponseParser.parse(xml, GRID, BASE);

        assertThat(response.status()).isEqualTo(Status.PERMANENT);
        assertThat(response.code()).isEqualTo("30");
        assertThat(response.message()).isEqualTo("SERVICE_KEY_IS_NOT_REGISTERED_ERROR");
    }

    @Test
    @DisplayName("빈 응답 · 읽을 수 없는 응답은 다시 시도")
    void 빈_응답() {
        assertThat(KmaResponseParser.parse("", GRID, BASE).status()).isEqualTo(Status.RETRYABLE);
        assertThat(KmaResponseParser.parse("{not json", GRID, BASE).status()).isEqualTo(Status.RETRYABLE);
    }

    private static String ok(String items) {
        return "{\"response\":{\"header\":{\"resultCode\":\"00\",\"resultMsg\":\"NORMAL_SERVICE\"},"
                + "\"body\":{\"dataType\":\"JSON\",\"items\":{\"item\":[" + items + "]},"
                + "\"pageNo\":1,\"numOfRows\":1000,\"totalCount\":7}}}";
    }

    private static String header(String code, String message) {
        return "{\"response\":{\"header\":{\"resultCode\":\"" + code + "\",\"resultMsg\":\"" + message + "\"}}}";
    }

    private static String item(String fcstTime, String category, String value) {
        return "{\"baseDate\":\"20260920\",\"baseTime\":\"1400\",\"category\":\"" + category + "\","
                + "\"fcstDate\":\"20260920\",\"fcstTime\":\"" + fcstTime + "\",\"fcstValue\":\"" + value + "\","
                + "\"nx\":60,\"ny\":127}";
    }
}
