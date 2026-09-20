package com.pawtrail.weather.domain.rule;

import com.pawtrail.weather.domain.enums.RegionMatch;
import com.pawtrail.weather.domain.model.RegionGridRow;
import com.pawtrail.weather.domain.model.RegionResolution;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 시도 코드와 시군구 이름을 기상청 격자로 바꿉니다.
 *
 * 검색 화면이 보내는 지역 값(search 지역 목록의 시도 코드 · 시군구 이름)을 그대로 받습니다.
 * 표는 기상청 활용가이드의 격자 위경도 표(2026.7.1 판)에서 시도 · 시군구 행만 옮긴 것입니다.
 *
 * 찾는 순서
 *   시군구 이름이 없으면 시도 대표
 *   같은 이름 (띄어쓰기를 빼고 비교 — 표는 고양시덕양구, 우리는 고양시 덕양구)
 *   표 이름이 우리 이름으로 시작 (우리는 시까지만 · 표에는 그 시의 구만 — 표 순서의 첫 구)
 *   우리 이름이 표 이름으로 시작 (우리는 시와 구 · 표에는 시만)
 *   그래도 없으면 시도 대표로 떨어뜨림 — 날씨가 아예 안 뜨는 것보다 넓은 지역의 날씨가 나음
 *   시도 코드 자체를 모르면 빈 값 — 부르는 쪽이 400 으로 답함
 */
public final class RegionGridTable {

    // 2026.7 표는 광주 · 전남을 12 전남광주통합특별시 하나로 묶음
    // 우리 장소 데이터는 옛 코드 29 · 46 을 씀 (place AddressNormalizer 가 되돌림)
    // 두 쪽 시군구 이름이 겹치지 않아 한데 모아도 서로 섞이지 않음
    private static final Map<String, String> SIDO_ALIAS = Map.of(
            "29", "12",
            "46", "12");

    // 2026.7 인천 개편 — 옛 동구는 통째로 제물포구가 됨
    // 옛 중구 · 서구는 둘로 갈려 어느 한쪽으로 보낼 수 없어 시도 대표로 떨어뜨림
    private static final Map<String, String> SIGUNGU_ALIAS = Map.of(
            "28:동구", "제물포구");

    private final Map<String, RegionGridRow> sidoRows = new HashMap<>();
    private final Map<String, List<RegionGridRow>> sigunguRows = new LinkedHashMap<>();

    public RegionGridTable(List<RegionGridRow> rows) {
        for (RegionGridRow row : rows) {
            if (row.isSido()) {
                sidoRows.put(row.sidoCode(), row);
            } else {
                sigunguRows.computeIfAbsent(row.sidoCode(), k -> new ArrayList<>()).add(row);
            }
        }
    }

    public int sidoCount() {
        return sidoRows.size();
    }

    public int sigunguCount() {
        return sigunguRows.values().stream().mapToInt(List::size).sum();
    }

    /**
     * 지역을 격자로 바꿉니다.
     *
     * @param sidoCode    시도 코드입니다 (법정동 두 자리).
     * @param sigunguName 시군구 이름입니다. 없으면 시도 대표 격자입니다.
     * @return 시도 코드를 모르면 빈 값
     */
    public Optional<RegionResolution> resolve(String sidoCode, String sigunguName) {
        if (sidoCode == null || sidoCode.isBlank()) {
            return Optional.empty();
        }
        String code = sidoCode.strip();
        String tableCode = SIDO_ALIAS.getOrDefault(code, code);
        RegionGridRow sido = sidoRows.get(tableCode);
        if (sido == null) {
            return Optional.empty();
        }
        if (sigunguName == null || sigunguName.isBlank()) {
            return Optional.of(new RegionResolution(sidoCode, null, RegionMatch.SIDO, sido.grid()));
        }
        String asked = sigunguName.strip();
        String wanted = normalize(SIGUNGU_ALIAS.getOrDefault(code + ":" + asked, asked));
        List<RegionGridRow> candidates = sigunguRows.getOrDefault(tableCode, List.of());

        Optional<RegionGridRow> found = candidates.stream()
                .filter(row -> normalize(row.sigunguName()).equals(wanted))
                .findFirst();
        if (found.isEmpty()) {
            found = candidates.stream()
                    .filter(row -> normalize(row.sigunguName()).startsWith(wanted))
                    .findFirst();
        }
        if (found.isEmpty()) {
            found = candidates.stream()
                    .filter(row -> wanted.startsWith(normalize(row.sigunguName())))
                    .findFirst();
        }
        return Optional.of(found
                .map(row -> new RegionResolution(sidoCode, sigunguName, RegionMatch.SIGUNGU, row.grid()))
                .orElseGet(() -> new RegionResolution(sidoCode, sigunguName, RegionMatch.SIDO, sido.grid())));
    }

    static String normalize(String name) {
        return name.replaceAll("\\s+", "");
    }
}
