package com.pawtrail.weather.presentation.controller;

import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.weather.application.dto.output.WeatherOutput;
import com.pawtrail.weather.application.service.WeatherService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 날씨를 돌려주는 공개 API 입니다.
 *
 * 경로는 하나이고 두 방식으로 부릅니다 (place 색인용 조회의 「경로 하나 두 방식」 선례).
 *   좌표    ?lat=&lon=                     첫 화면 · 장소 상세
 *   지역    ?sidoCode=&sigunguName=         검색 결과 · sigunguName 을 빼면 시도 대표
 * 둘을 함께 주거나, 둘 다 안 주거나, 좌표를 하나만 주거나, sigunguName 만 주면 400 입니다.
 */
@RestController
@RequestMapping("/api/v1/weather")
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping
    public ResponseEntity<CommonApiResponse<WeatherOutput>> weather(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon,
            @RequestParam(required = false) String sidoCode,
            @RequestParam(required = false) String sigunguName) {

        boolean coordinates = lat != null || lon != null;
        boolean region = hasText(sidoCode) || hasText(sigunguName);
        if (coordinates == region) {
            throw new CustomException(CommonErrorCode.VALIDATION_FAILED);
        }
        if (coordinates) {
            if (lat == null || lon == null) {
                throw new CustomException(CommonErrorCode.VALIDATION_FAILED);
            }
            return ResponseEntity.ok(CommonApiResponse.success(weatherService.byCoordinates(lat, lon)));
        }
        if (!hasText(sidoCode)) {
            throw new CustomException(CommonErrorCode.VALIDATION_FAILED);
        }
        String sigungu = hasText(sigunguName) ? sigunguName.strip() : null;
        return ResponseEntity.ok(CommonApiResponse.success(weatherService.byRegion(sidoCode.strip(), sigungu)));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
