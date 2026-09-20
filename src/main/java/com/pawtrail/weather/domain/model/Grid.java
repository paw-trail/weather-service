package com.pawtrail.weather.domain.model;

/**
 * 기상청 단기예보의 5km 격자 한 칸입니다.
 *
 * 전국이 동서 149 · 남북 253 칸으로 나뉘며 번호는 1 부터 셉니다.
 * 같은 칸이면 예보가 같으므로 캐시 열쇠도 이 두 값으로 만듭니다.
 *
 * @param nx 동서 번호입니다.
 * @param ny 남북 번호입니다.
 */
public record Grid(int nx, int ny) {
}
