package com.rideshare.matchingservice.service;

import com.rideshare.matchingservice.client.LocationServiceClient;
import com.rideshare.matchingservice.dto.DriverClaimRequest;
import com.rideshare.matchingservice.dto.DriverClaimResponse;
import com.rideshare.matchingservice.dto.NearByDriverResponse;
import com.rideshare.matchingservice.event.RideMatchedEvent;
import com.rideshare.matchingservice.event.RideRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class MatchingService {

    private final LocationServiceClient locationServiceClient;
    private final KafkaTemplate<String, RideMatchedEvent> kafkaTemplate;

    private static final String RIDE_MATCHED_TOPIC = "ride.matched";
    private static final double DEFAULT_SEARCH_RADIUS_KM = 5.0;

    /**
     * Main matching alogorith
     * Called when RideRequestedEvent is consumed from Kafka
     * @param event
     *
     * STEPS:
     * 1. Ask Location Service for nearby drivers
     * 2. Score each driver and pick the best one
     */

    public void matchDriverForRide(RideRequestedEvent event){

        List<NearByDriverResponse> nearByDrivers = locationServiceClient.getNearByDrivers(
                event.getPickupLatitude(),
                event.getPickupLongitude(),
                DEFAULT_SEARCH_RADIUS_KM
        );

        if(nearByDrivers.isEmpty()){
            log.warn("No drivers found near ride");
            return;
        }

        // STEP 2: Score each driver and pick the best one
        List<NearByDriverResponse> rankedDrivers =
                rankDrivers(nearByDrivers);

        if (rankedDrivers.isEmpty()) {
            log.warn("Could not find suitable driver for ride {}", event.getRideId());
            return;
        }

        // STEP 3: Try to claim drivers in ranking order
        for (NearByDriverResponse driver : rankedDrivers) {

            DriverClaimRequest request = new DriverClaimRequest();
            request.setRideId(event.getRideId());

            DriverClaimResponse response =
                    locationServiceClient.claimDriver(
                            driver.getDriverId(),
                            request
                    );

            if (!response.isClaimed()) {
                log.info(
                        "Driver {} could not be claimed. Trying next driver.",
                        driver.getDriverId()
                );
                continue;
            }

            // STEP 4: We successfully claimed this driver
            RideMatchedEvent matchedEvent = new RideMatchedEvent(
                    event.getRideId(),
                    event.getRiderId(),
                    driver.getDriverId(),
                    driver.getLatitude(),
                    driver.getLongitude(),
                    driver.getDistanceInKm()
            );

            // STEP 5: Publish only after successful claim
            kafkaTemplate.send(
                    RIDE_MATCHED_TOPIC,
                    event.getRideId(),
                    matchedEvent
            );

            log.info(
                    "Driver {} claimed successfully for ride {}. RideMatchedEvent published.",
                    driver.getDriverId(),
                    event.getRideId()
            );

            return;
        }

        // All candidate drivers failed to claim
        log.warn(
                "No available driver could be claimed for ride {}",
                event.getRideId()
        );
    }

    /**
     * Driver Scoring algorithm
     *
     * Distance: 70%
     * Rating: 30%
     *
     * Score = (1 / distance) * distanceWeight + rating * ratingWeight
     *
//     * @param drivers
//     * @return
     */

    private List<NearByDriverResponse> rankDrivers(
            List<NearByDriverResponse> drivers) {

        double distanceWeight = 0.7;
        double ratingWeight = 0.3;

        return drivers.stream()
                .sorted(
                        Comparator.comparingDouble(
                                (NearByDriverResponse driver) -> {

                                    double distanceScore =
                                            1.0 / (driver.getDistanceInKm() + 0.1);

                                    double simulatedRating =
                                            4.0 + Math.random();

                                    return (distanceScore * distanceWeight)
                                            + (simulatedRating * ratingWeight);
                                }
                        ).reversed()
                )
                .toList();
    }

}
