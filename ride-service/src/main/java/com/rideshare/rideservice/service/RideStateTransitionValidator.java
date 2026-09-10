package com.rideshare.rideservice.service;

import com.rideshare.rideservice.model.RideStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RideStateTransitionValidator {

    public void validate(RideStatus currentStatus, RideStatus newStatus) {

        if (currentStatus == null || newStatus == null) {
            throw new IllegalArgumentException("Ride status cannot be null");
        }

        boolean valid = switch (currentStatus) {

            case REQUESTED ->
                    newStatus == RideStatus.MATCHING
                            || newStatus == RideStatus.CANCELLED;

            case MATCHING ->
                    newStatus == RideStatus.ACCEPTED
                            || newStatus == RideStatus.CANCELLED;

            case ACCEPTED ->
                    newStatus == RideStatus.DRIVER_ARRIVING
                            || newStatus == RideStatus.CANCELLED;

            case DRIVER_ARRIVING ->
                    newStatus == RideStatus.RIDE_STARTED
                            || newStatus == RideStatus.CANCELLED;

            case RIDE_STARTED ->
                    newStatus == RideStatus.COMPLETED
                            || newStatus == RideStatus.CANCELLED;

            case COMPLETED, CANCELLED ->
                    false;
        };

        if (!valid) {
            throw new IllegalStateException(
                    "Invalid ride state transition: "
                            + currentStatus
                            + " → "
                            + newStatus
            );
        }
    }
}