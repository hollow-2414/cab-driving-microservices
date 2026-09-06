package com.rideshare.locationservice.service;

import com.rideshare.locationservice.dto.DriverLocationRequest;
import com.rideshare.locationservice.dto.NearByDriverResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private GeoOperations<String, String> geoOperations;

    @InjectMocks
    private LocationService locationService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForGeo()).thenReturn(geoOperations);
    }

    @Test
    @DisplayName("updateDriverLocation - Adds driver point to Redis Geo spatial index")
    void testUpdateDriverLocation() {
        DriverLocationRequest request = new DriverLocationRequest("driver-101", 12.9716, 77.5946);

        locationService.updateDriverLocation(request);

        verify(geoOperations, times(1)).add(
                eq("drivers:location"),
                eq(new Point(77.5946, 12.9716)),
                eq("driver-101")
        );
    }

    @Test
    @DisplayName("findNearbyDrivers - Returns nearby drivers list sorted by distance")
    void testFindNearbyDrivers_Success() {
        RedisGeoCommands.GeoLocation<String> geoLocation = new RedisGeoCommands.GeoLocation<>("driver-101", new Point(77.5946, 12.9716));
        GeoResult<RedisGeoCommands.GeoLocation<String>> geoResult = new GeoResult<>(geoLocation, new Distance(1.2));
        GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults = new GeoResults<>(List.of(geoResult));

        when(geoOperations.radius(eq("drivers:location"), any(Circle.class), any(RedisGeoCommands.GeoRadiusCommandArgs.class)))
                .thenReturn(geoResults);

        List<NearByDriverResponse> response = locationService.findNearbyDrivers(12.9716, 77.5946, 5.0);

        assertNotNull(response);
        assertEquals(1, response.size());
        assertEquals("driver-101", response.get(0).getDriverId());
        assertEquals(12.9716, response.get(0).getLatitude());
        assertEquals(77.5946, response.get(0).getLongitude());
        assertEquals(1.2, response.get(0).getDistanceInKm());
    }

    @Test
    @DisplayName("findNearbyDrivers - Returns empty list when no drivers nearby")
    void testFindNearbyDrivers_Empty() {
        GeoResults<RedisGeoCommands.GeoLocation<String>> emptyResults = new GeoResults<>(Collections.emptyList());

        when(geoOperations.radius(eq("drivers:location"), any(Circle.class), any(RedisGeoCommands.GeoRadiusCommandArgs.class)))
                .thenReturn(emptyResults);

        List<NearByDriverResponse> response = locationService.findNearbyDrivers(12.9716, 77.5946, 5.0);

        assertNotNull(response);
        assertTrue(response.isEmpty());
    }

    @Test
    @DisplayName("findNearbyDrivers - Handles null result from Redis operations")
    void testFindNearbyDrivers_NullResults() {
        when(geoOperations.radius(eq("drivers:location"), any(Circle.class), any(RedisGeoCommands.GeoRadiusCommandArgs.class)))
                .thenReturn(null);

        List<NearByDriverResponse> response = locationService.findNearbyDrivers(12.9716, 77.5946, 5.0);

        assertNotNull(response);
        assertTrue(response.isEmpty());
    }

    @Test
    @DisplayName("removeDriver - Invokes geo remove with driver ID")
    void testRemoveDriver() {
        locationService.removeDriver("driver-101");

        verify(geoOperations, times(1)).remove("drivers:location", "driver-101");
    }
}
