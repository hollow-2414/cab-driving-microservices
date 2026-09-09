package com.rideshare.locationservice.controller;

import com.rideshare.locationservice.dto.DriverClaimRequest;
import com.rideshare.locationservice.dto.DriverClaimResponse;
import com.rideshare.locationservice.service.DriverClaimService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/drivers")
@RequiredArgsConstructor
public class DriverClaimController {

    private final DriverClaimService driverClaimService;

    @PostMapping("/{driverId}/claim")
    public DriverClaimResponse claimDriver(
            @PathVariable String driverId,
            @RequestBody DriverClaimRequest request
    ) {

        boolean claimed = driverClaimService.claimDriver(
                driverId,
                request.getRideId()
        );

        return new DriverClaimResponse(claimed);
    }
}
