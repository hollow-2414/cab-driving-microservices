package com.rideshare.locationservice.service;

import com.rideshare.locationservice.dto.DriverLocationRequest;
import com.rideshare.locationservice.dto.NearByDriverResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class LocationService {

        private final RedisTemplate<String, String> redisTemplate;

        // Redis key for All the driver locations yrr
        private static final String DRIVERS_GEO_KEY = "drivers:location";

        /**
         * Update karna hai driver ke location ko Redis me
         * every 3 seconds me call karna hai drivers ke phone se
         * Maps to Redis by GEOADD command
         */

        public void updateDriverLocation(
                        DriverLocationRequest driverLocationRequest) {
                log.info("Updating location for driver : {}",
                                driverLocationRequest.getDriverId());
                // imposrtant : longitude FIRST, latitude SECOND - GeoSpatial Standard

                Point driverPoint = new Point(
                                driverLocationRequest.getLongitude(),
                                driverLocationRequest.getLatitude());

                redisTemplate.opsForGeo().add(
                                DRIVERS_GEO_KEY,
                                driverPoint,
                                driverLocationRequest.getDriverId());

                /**
                 * Find nearby drivers within given radius.
                 * Called by Matching Service on ride request.
                 * Maps to Redis FEORADIUS command.
                 */

        }

        public List<NearByDriverResponse> findNearbyDrivers(
                        double latitude,
                        double longitude,
                        double radiusInKm) {
                Circle searchArea = new Circle(
                                new Point(longitude, latitude),
                                new Distance(radiusInKm, Metrics.KILOMETERS));

                GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate.opsForGeo().radius(
                                DRIVERS_GEO_KEY,
                                searchArea,
                                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                                .includeCoordinates()
                                                .includeDistance()
                                                .sortAscending()
                                                .limit(10));

                List<NearByDriverResponse> nearbyDrivers = new ArrayList<>();

                if (results != null) {
                        results.getContent().forEach(result -> {
                                RedisGeoCommands.GeoLocation<String> location = result.getContent();
                                nearbyDrivers.add(new NearByDriverResponse(
                                                location.getName(),
                                                location.getPoint().getY(),
                                                location.getPoint().getX(),
                                                result.getDistance().getValue()));
                        });
                }
                log.info("Found {} drivers nearby", nearbyDrivers.size());
                return nearbyDrivers;

        }

        /**
         * remove the driver when the driver goes offline
         * maps to Redis ZREM command
         */

        public void removeDriver(String driverId) {
                log.info("Removing driver: {}", driverId);
                redisTemplate.opsForGeo().remove(DRIVERS_GEO_KEY, driverId);
        }

}
