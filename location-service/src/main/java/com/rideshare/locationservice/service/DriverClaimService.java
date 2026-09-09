package com.rideshare.locationservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DriverClaimService {

    private final StringRedisTemplate redisTemplate;

    private static final String CLAIM_SCRIPT = """
    if redis.call('EXISTS', KEYS[1]) == 1 then
        return 0
    end

    if redis.call('EXISTS', KEYS[2]) == 1 then
        return 0
    end

    redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[3])
    redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[3])

    return 1
    """;

    private static final String RELEASE_SCRIPT = """
    if redis.call('GET', KEYS[1]) ~= ARGV[1] then
        return 0
    end

    if redis.call('GET', KEYS[2]) ~= ARGV[2] then
        return 0
    end

    redis.call('DEL', KEYS[1])
    redis.call('DEL', KEYS[2])

    return 1
    """;

    private static final long CLAIM_TTL_SECONDS = 30;

    public boolean claimDriver(String driverId, String rideId) {

        String driverKey = "driver:claim:" + driverId;
        String rideKey = "ride:claim:" + rideId;

        Long claimed = redisTemplate.execute(
                RedisScript.of(CLAIM_SCRIPT, Long.class),
                List.of(driverKey, rideKey),
                rideId,
                driverId,
                String.valueOf(CLAIM_TTL_SECONDS)
        );

        return Long.valueOf(1).equals(claimed);
    }

    public boolean releaseDriver(String driverId, String rideId) {

        String driverKey = "driver:claim:" + driverId;
        String rideKey = "ride:claim:" + rideId;

        Long result = redisTemplate.execute(
                RedisScript.of(RELEASE_SCRIPT, Long.class),
                List.of(driverKey, rideKey),
                rideId,
                driverId
        );

        return Long.valueOf(1).equals(result);
    }
}
