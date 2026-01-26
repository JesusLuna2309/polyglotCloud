package com.jesusLuna.polyglotCloud.service;

import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    public void save(String key, Object value, long timeout, TimeUnit unit) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout, unit);
            log.debug("Cached data with key: {}", key);
        } catch (Exception e) {
            log.error("Error caching data with key: {}", key, e);
        }
    }

    public Object get(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("Error retrieving cached data with key: {}", key, e);
            return null;
        }
    }

    public void delete(String key) {
        try {
            redisTemplate.delete(key);
            log.debug("Deleted cache with key: {}", key);
        } catch (Exception e) {
            log.error("Error deleting cached data with key: {}", key, e);
        }
    }

    public void deletePattern(String pattern) {
        try {
            var keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("Deleted {} cache entries matching pattern: {}", keys.size(), pattern);
            }
        } catch (Exception e) {
            log.error("Error deleting cached data with pattern: {}", pattern, e);
        }
    }
}