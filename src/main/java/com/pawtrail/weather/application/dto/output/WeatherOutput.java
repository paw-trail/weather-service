package com.pawtrail.weather.application.dto.output;

import com.pawtrail.weather.domain.enums.PrecipitationType;
import com.pawtrail.weather.domain.enums.RegionMatch;
import com.pawtrail.weather.domain.enums.SkyCondition;
import com.pawtrail.weather.domain.model.HourlyForecast;
import com.pawtrail.weather.domain.model.RainToday;
import com.pawtrail.weather.domain.model.RegionResolution;
import java.time.LocalDateTime;

/**
 * 날씨 응답입니다.
 *
 * 첫 화면 배너 · 검색 결과 · 장소 상세가 같은 모양을 받습니다.
 *
 * @param baseAt    이 값이 나온 발표 시각입니다.
 * @param stale     새 발표를 받지 못해 직전 발표를 내준 것이면 true 입니다.
 * @param at        지금으로 고른 예보 시각입니다.
 * @param tmp       기온(℃)입니다.
 * @param sky       하늘 상태입니다.
 * @param pty       강수 형태입니다.
 * @param pop       강수 확률(%)입니다.
 * @param rainToday 오늘 남은 시간의 첫 비 · 눈 예보입니다. 없으면 null 입니다.
 * @param region    지역으로 불렀을 때만 실립니다. 좌표로 부르면 null 입니다.
 */
public record WeatherOutput(LocalDateTime baseAt,
                            boolean stale,
                            LocalDateTime at,
                            Integer tmp,
                            SkyCondition sky,
                            PrecipitationType pty,
                            Integer pop,
                            RainTodayOutput rainToday,
                            RegionOutput region) {

    public static WeatherOutput of(LocalDateTime baseAt, boolean stale, HourlyForecast current,
                                   RainToday rainToday, RegionResolution region) {
        return new WeatherOutput(
                baseAt,
                stale,
                current.at(),
                current.tmp(),
                current.sky(),
                current.pty(),
                current.pop(),
                rainToday == null ? null : new RainTodayOutput(rainToday.firstAt(), rainToday.type()),
                region == null ? null : new RegionOutput(region.sidoCode(), region.sigunguName(), region.matched()));
    }

    /**
     * @param firstAt 비나 눈이 예보된 첫 시각입니다.
     * @param type    그 시각의 강수 형태입니다.
     */
    public record RainTodayOutput(LocalDateTime firstAt, PrecipitationType type) {
    }

    /**
     * @param sidoCode    요청한 시도 코드입니다.
     * @param sigunguName 요청한 시군구 이름입니다. 보내지 않았으면 null 입니다.
     * @param matched     SIGUNGU 면 그 시군구 · SIDO 면 시도 대표 격자를 썼다는 뜻입니다.
     */
    public record RegionOutput(String sidoCode, String sigunguName, RegionMatch matched) {
    }
}
