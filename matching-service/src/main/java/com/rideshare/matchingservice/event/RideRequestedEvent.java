package com.rideshare.matchingservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Event consumed from kafka topic: ride.requested
 * Published by Ride Service when a rider requests a ride
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RideRequestedEvent {

    private String riderId;
    private String rideId;
    private double pickupLatitude;
    private double pickupLongitude;
    private String pickupAddress;
    private double dropLatitude;
    private double dropLongitude;
    private String dropAddress;
}
