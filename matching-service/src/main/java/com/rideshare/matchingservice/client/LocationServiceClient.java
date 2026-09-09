package com.rideshare.matchingservice.client;

import com.rideshare.matchingservice.dto.DriverClaimRequest;
import com.rideshare.matchingservice.dto.DriverClaimResponse;
import com.rideshare.matchingservice.dto.NearByDriverResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "location-service", url = "${location.service.url}")
public interface LocationServiceClient {

    @GetMapping("/api/v1/locations/drivers/nearby")
    List<NearByDriverResponse> getNearByDrivers(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam double radius
    );

    @PostMapping("/api/v1/drivers/{driverId}/claim")
    DriverClaimResponse claimDriver(
            @PathVariable String driverId,
            @RequestBody DriverClaimRequest request
    );
}
