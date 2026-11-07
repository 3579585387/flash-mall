package com.flashmall.controller;

import com.flashmall.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 环境自检
 * <p>
 * 启动后先访问 /api/health：能一眼看出是 MySQL 没连上、Redis 没启动，
 * 还是都正常，省去翻日志的时间。
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthController {

    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();

        try {
            redis.opsForValue().set("flash-mall:health", "1", Duration.ofSeconds(10));
            result.put("redis", "OK");
        } catch (Exception e) {
            result.put("redis", "FAIL: " + e.getMessage());
        }

        try {
            Integer one = jdbcTemplate.queryForObject("select 1", Integer.class);
            result.put("mysql", one != null && one == 1 ? "OK" : "UNKNOWN");
            Integer count = jdbcTemplate.queryForObject("select count(1) from tb_goods", Integer.class);
            result.put("goodsCount", count);
        } catch (Exception e) {
            result.put("mysql", "FAIL: " + e.getMessage());
        }

        return Result.ok(result);
    }
}
