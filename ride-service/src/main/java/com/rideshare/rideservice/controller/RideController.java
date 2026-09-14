package com.rideshare.rideservice.controller;



import com.rideshare.rideservice.dto.RideRequest;
import com.rideshare.rideservice.dto.RideResponse;
import com.rideshare.rideservice.service.RideService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rides")
@Slf4j
@RequiredArgsConstructor
public class RideController {

    private final RideService rideService;

    // wHEN Rider requests a new Ride

    @PreAuthorize("hasRole('RIDER')")
    @PostMapping("/request")
    public ResponseEntity<RideResponse> requestRide(
            @Valid @RequestBody RideRequest rideRequest
    ){
        log.info("Ride request recieved from Rider : {}",
                rideRequest.getRiderId());
        return ResponseEntity.ok(rideService.requestRide(rideRequest));
    }

//    Get details of one ride

    @GetMapping("/{rideId}")
    public ResponseEntity<RideResponse> getRideById(
            @PathVariable String rideId
    ) {
        return ResponseEntity.ok(rideService.getRideById(rideId));
    }

//    Get all rides of a rider

    @PreAuthorize("hasRole('RIDER')")
    @GetMapping("/rider/{riderId}")
    public ResponseEntity<List<RideResponse>> getRidesByRider(
            @PathVariable String riderId
    ){
        return ResponseEntity.ok(rideService.getRidesByRider(riderId));
    }

    // Driver starts the Ride
    @PreAuthorize("hasRole('DRIVER')")
    @PutMapping("/{rideId}/start")
    public ResponseEntity<RideResponse> startRide(
            @PathVariable String rideId){
        return ResponseEntity.ok(rideService.startRide(rideId));
    }

    // Driver Arriving
    @PreAuthorize("hasRole('DRIVER')")
    @PutMapping("/{rideId}/arriving")
    public ResponseEntity<RideResponse> driverArriving(
            @PathVariable String rideId) {

        return ResponseEntity.ok(
                rideService.driverArriving(rideId)
        );
    }


    // Driver Completes the Ride
    @PreAuthorize("hasRole('DRIVER')")
    @PutMapping("/{rideId}/complete")
    public ResponseEntity<RideResponse> completeRide(
            @PathVariable String rideId){
        return ResponseEntity.ok(rideService.completeRide(rideId));
    }


    // Driver Cancels the Ride
    @PutMapping("/{rideId}/cancel")
    public ResponseEntity<RideResponse> cancelRide(
            @PathVariable String rideId){
        return ResponseEntity.ok(rideService.cancelRide(rideId));
    }


}