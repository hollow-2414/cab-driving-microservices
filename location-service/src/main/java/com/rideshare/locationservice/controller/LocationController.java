package com.rideshare.locationservice.controller;

import com.rideshare.locationservice.dto.DriverLocationRequest;
import com.rideshare.locationservice.dto.NearByDriverResponse;
import com.rideshare.locationservice.service.LocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/locations")
@Slf4j
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    // call this by driver phone in every 3 seconds
    @PostMapping("/drivers/update")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<String> updateDriverLocation(
            @RequestBody DriverLocationRequest driverLocationRequest) {
        locationService.updateDriverLocation(driverLocationRequest);
        return ResponseEntity.ok("Driver location updating");
    }

    // Matching service calls this when ride is requested
    @GetMapping("/drivers/nearby")
    public ResponseEntity<List<NearByDriverResponse>> getNearByDrivers(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "5.0") double radius) {
        return ResponseEntity.ok(locationService.findNearbyDrivers(latitude, longitude, radius));
    }

    // called when driver goes offline
    @DeleteMapping("/drivers/{driverID}")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<String> removeDriver(@PathVariable String driverID) {
        locationService.removeDriver(driverID);
        return ResponseEntity.ok("Driver removed successfully");
    }

}
