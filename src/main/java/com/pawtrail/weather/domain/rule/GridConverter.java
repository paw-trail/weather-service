package com.pawtrail.weather.domain.rule;

import com.pawtrail.weather.domain.model.Grid;
import java.util.Optional;

/**
 * 위경도를 기상청 단기예보 격자로 바꿉니다.
 *
 * 기상청 활용가이드에 실린 C 원문(lamcproj)을 옮긴 것입니다.
 * 람베르트 정각원추도법으로 좌표를 평면에 펴고 5km 칸 번호로 잘라냅니다.
 * 상수는 가이드 값 그대로이며 바꾸면 기상청 격자와 어긋납니다.
 *
 * 가이드의 격자 표 3,838행 가운데 3,793행이 이 식과 칸까지 같습니다.
 * 나머지 45행은 칸 경계에서 한 칸씩 갈리며, 표가 위치를 따로 고친 행들로 보입니다.
 * 지역으로 부를 때는 이 식이 아니라 표의 격자를 그대로 씁니다.
 */
public final class GridConverter {

    // 가이드의 지도 정보 — 지구 반경(km) · 격자 간격(km) · 표준위도 둘 · 기준점 경도 · 위도
    private static final double RE = 6371.00877;
    private static final double GRID = 5.0;
    private static final double SLAT1 = 30.0;
    private static final double SLAT2 = 60.0;
    private static final double OLON = 126.0;
    private static final double OLAT = 38.0;

    // 기준점의 격자 좌표 (가이드 xo = 210 / grid · yo = 675 / grid)
    private static final double XO = 210 / GRID;
    private static final double YO = 675 / GRID;

    // 격자 범위 (가이드 NX · NY)
    public static final int MAX_X = 149;
    public static final int MAX_Y = 253;

    private static final double PI = Math.asin(1.0) * 2.0;
    private static final double DEGRAD = PI / 180.0;
    private static final double RE_GRID = RE / GRID;
    private static final double OLON_RAD = OLON * DEGRAD;
    private static final double SN;
    private static final double SF;
    private static final double RO;

    static {
        double slat1 = SLAT1 * DEGRAD;
        double slat2 = SLAT2 * DEGRAD;
        double olat = OLAT * DEGRAD;
        double sn = Math.tan(PI * 0.25 + slat2 * 0.5) / Math.tan(PI * 0.25 + slat1 * 0.5);
        sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn);
        double sf = Math.tan(PI * 0.25 + slat1 * 0.5);
        sf = Math.pow(sf, sn) * Math.cos(slat1) / sn;
        double ro = Math.tan(PI * 0.25 + olat * 0.5);
        ro = RE_GRID * sf / Math.pow(ro, sn);
        SN = sn;
        SF = sf;
        RO = ro;
    }

    private GridConverter() {
    }

    /**
     * 위경도를 격자로 바꿉니다.
     *
     * 격자 밖(우리나라 바깥)이거나 숫자가 아니면 빈 값을 돌려줍니다. 부르는 쪽이 400 으로 답합니다.
     *
     * @param lat 위도(도)입니다.
     * @param lon 경도(도)입니다.
     */
    public static Optional<Grid> toGrid(double lat, double lon) {
        if (!Double.isFinite(lat) || !Double.isFinite(lon)
                || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            return Optional.empty();
        }
        double ra = Math.tan(PI * 0.25 + lat * DEGRAD * 0.5);
        ra = RE_GRID * SF / Math.pow(ra, SN);
        double theta = lon * DEGRAD - OLON_RAD;
        if (theta > PI) {
            theta -= 2.0 * PI;
        }
        if (theta < -PI) {
            theta += 2.0 * PI;
        }
        theta *= SN;
        double x = ra * Math.sin(theta) + XO;
        double y = RO - ra * Math.cos(theta) + YO;

        // 가이드 원문 그대로 1.5 를 더해 자름 — 번호가 1 부터라 1 을, 가장 가까운 칸으로 반올림하려고 0.5 를 더함
        int nx = (int) (x + 1.5);
        int ny = (int) (y + 1.5);
        if (nx < 1 || nx > MAX_X || ny < 1 || ny > MAX_Y) {
            return Optional.empty();
        }
        return Optional.of(new Grid(nx, ny));
    }
}
