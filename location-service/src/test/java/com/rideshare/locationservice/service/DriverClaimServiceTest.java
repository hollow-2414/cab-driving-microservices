package com.rideshare.locationservice.service;

import com.rideshare.locationservice.controller.DriverClaimController;
import com.rideshare.locationservice.dto.DriverClaimRequest;
import com.rideshare.locationservice.dto.DriverClaimResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DriverClaimServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private DriverClaimService driverClaimService;

    @Test
    @DisplayName("Test 5B - Stale release protection script execution flow")
    void testStaleReleaseProtection_Flow() {
        // 1. Claim D4 with R300 -> true
        doReturn(1L).when(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("driver:claim:D4", "ride:claim:R300")),
                eq("R300"), eq("D4"), eq("30")
        );

        boolean claimR300 = driverClaimService.claimDriver("D4", "R300");
        assertTrue(claimR300, "Step 1: Claiming D4 with R300 should return true");

        // 3. Claim D4 with R400 -> true
        doReturn(1L).when(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("driver:claim:D4", "ride:claim:R400")),
                eq("R400"), eq("D4"), eq("30")
        );

        boolean claimR400 = driverClaimService.claimDriver("D4", "R400");
        assertTrue(claimR400, "Step 3: Claiming D4 with R400 should return true");

        // 4. Simulate OLD R300 release -> false
        doReturn(0L).when(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("driver:claim:D4", "ride:claim:R300")),
                eq("R300"), eq("D4")
        );

        boolean releaseR300 = driverClaimService.releaseDriver("D4", "R300");
        assertFalse(releaseR300, "Step 4: Releasing D4 with stale R300 should return false");
    }

    @Test
    @DisplayName("Test 5B - Full Stale Release Protection with Controller & Redis State Simulation")
    void testStaleReleaseProtection_InMemoryRedisSimulationWithController() {
        Map<String, String> redisStore = new HashMap<>();

        doAnswer(invocation -> {
            RedisScript<?> script = invocation.getArgument(0);
            List<String> keys = invocation.getArgument(1);
            Object[] args = new Object[invocation.getArguments().length - 2];
            for (int i = 2; i < invocation.getArguments().length; i++) {
                args[i - 2] = invocation.getArgument(i);
            }

            String scriptText = script.getScriptAsString();

            // CLAIM_SCRIPT logic:
            if (scriptText.contains("EXISTS")) {
                String k1 = keys.get(0);
                String k2 = keys.get(1);
                if (redisStore.containsKey(k1) || redisStore.containsKey(k2)) {
                    return 0L;
                }
                redisStore.put(k1, (String) args[0]);
                redisStore.put(k2, (String) args[1]);
                return 1L;
            }

            // RELEASE_SCRIPT logic:
            if (scriptText.contains("DEL")) {
                String k1 = keys.get(0);
                String k2 = keys.get(1);
                String arg1 = (String) args[0];
                String arg2 = (String) args[1];

                if (!arg1.equals(redisStore.get(k1)) || !arg2.equals(redisStore.get(k2))) {
                    return 0L;
                }
                redisStore.remove(k1);
                redisStore.remove(k2);
                return 1L;
            }

            return 0L;
        }).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        DriverClaimController controller = new DriverClaimController(driverClaimService);

        // 1. Claim D4 with R300 -> expected true
        DriverClaimRequest req1 = new DriverClaimRequest();
        req1.setRideId("R300");
        DriverClaimResponse claim1 = controller.claimDriver("D4", req1);
        assertTrue(claim1.isClaimed(), "1. Claim D4 with R300: R300 -> D4 -> true");
        assertEquals("R300", redisStore.get("driver:claim:D4"));
        assertEquals("D4", redisStore.get("ride:claim:R300"));

        // 2. Wait for 30-second TTL to expire -> keys expire in Redis
        redisStore.clear();
        assertNull(redisStore.get("driver:claim:D4"), "2. GET driver:claim:D4 expected (nil)");

        // 3. Claim D4 with R400 -> POST /api/v1/drivers/D4/claim {"rideId": "R400"} -> expected claimed: true
        DriverClaimRequest req2 = new DriverClaimRequest();
        req2.setRideId("R400");
        DriverClaimResponse claim2 = controller.claimDriver("D4", req2);
        assertTrue(claim2.isClaimed(), "3. Claim D4 with R400: POST /api/v1/drivers/D4/claim -> claimed: true");
        assertEquals("R400", redisStore.get("driver:claim:D4"), "3. GET driver:claim:D4 expected R400");

        // 4. Now simulate the OLD R300 release: DELETE /api/v1/drivers/D4/claim?rideId=R300 -> expected claimed: false
        DriverClaimResponse releaseOld = controller.releaseDriver("D4", "R300");
        assertFalse(releaseOld.isClaimed(), "4. DELETE /api/v1/drivers/D4/claim?rideId=R300 -> claimed: false");

        // 5. Verify R400 survived
        assertEquals("R400", redisStore.get("driver:claim:D4"), "5. GET driver:claim:D4 must still be R400");
        assertEquals("D4", redisStore.get("ride:claim:R400"), "5. GET ride:claim:R400 must be D4");
    }
}
