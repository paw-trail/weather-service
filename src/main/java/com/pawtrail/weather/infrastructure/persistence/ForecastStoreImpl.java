package com.pawtrail.weather.infrastructure.persistence;

import com.pawtrail.weather.domain.model.ForecastRun;
import com.pawtrail.weather.domain.model.Grid;
import com.pawtrail.weather.domain.repository.ForecastStore;
import com.pawtrail.weather.infrastructure.config.WeatherProperties;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 발표 한 번을 Redis 에 담아 둡니다.
 *
 * 열쇠 — weather:forecast:{nx}:{ny}:{발표 yyyyMMddHHmm}
 * 수명 — app.weather.cache.ttl-hours (가장 새 발표로 3시간 · 직전 발표로 3시간 쓰임)
 *
 * Redis 에 닿지 못하면 없는 것으로 답하고 저장은 건너뜁니다.
 * 캐시가 죽어도 기상청을 직접 불러 날씨를 내줄 수 있어야 하기 때문입니다.
 */
@Repository
public class ForecastStoreImpl implements ForecastStore {

    private static final Logger log = LoggerFactory.getLogger(ForecastStoreImpl.class);

    static final String KEY_PREFIX = "weather:forecast:";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public ForecastStoreImpl(StringRedisTemplate redisTemplate, WeatherProperties properties) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofHours(properties.cache().ttlHours());
    }

    @Override
    public Optional<ForecastRun> find(Grid grid, LocalDateTime baseAt) {
        String key = key(grid, baseAt);
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value == null ? Optional.empty() : Optional.of(ForecastRunCodec.decode(value));
        } catch (DataAccessException e) {
            log.warn("캐시를 읽지 못해 없는 것으로 봄 key={} 까닭={}", key, e.getMessage());
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            log.warn("캐시 값의 모양이 달라 없는 것으로 봄 key={} 까닭={}", key, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void save(ForecastRun run) {
        String key = key(run.grid(), run.baseAt());
        try {
            redisTemplate.opsForValue().set(key, ForecastRunCodec.encode(run), ttl);
        } catch (DataAccessException e) {
            log.warn("캐시에 담지 못함 key={} 까닭={}", key, e.getMessage());
        }
    }

    static String key(Grid grid, LocalDateTime baseAt) {
        return KEY_PREFIX + grid.nx() + ":" + grid.ny() + ":" + baseAt.format(TIME);
    }
}
