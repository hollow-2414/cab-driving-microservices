package com.rideshare.matchingservice.client;

import com.rideshare.matchingservice.client.LocationServiceClient;
import com.rideshare.matchingservice.dto.NearByDriverResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import io.github.resilience4j.retry.annotation.Retry;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocationServiceResilientClient {

    private final LocationServiceClient locationServiceClient;

    @CircuitBreaker(name = "locationServiceCircuitBreaker")
    @Retry(name = "locationServiceRetry")
    public List<NearByDriverResponse> getNearbyDrivers(
            double latitude,
            double longitude,
            double radius
    ) {
        log.info("🔁 Location Service GET attempt");

        return locationServiceClient.getNearByDrivers(
                latitude,
                longitude,
                radius
        );
    }

}