package com.rideshare.matchingservice.service;

import com.rideshare.matchingservice.entity.EntityType;
import com.rideshare.matchingservice.event.RideRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class RideEventConsumer {

    private final MatchingService matchingService;
    private final IdempotencyService idempotencyService;

    /**
     * Listens to ride.requested kafka topic.
     * Triggered every time Ride Service published a new ride request
     * <p>
     * FLOW:
     * Ride Service -> Kafka (ride.requested) -> This Consumer -> MatchingService
     */

    @KafkaListener(
            topics = "ride.requested",
            groupId = "matching-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeRideRequestedEvent(RideRequestedEvent event) {


        log.info("🔥 MATCHING LISTENER CALLED: {}", event);


        String rideId = event.getRideId();

        if (idempotencyService.isProcessed(rideId)) {

            log.info(
                    "Ignoring duplicate ride request: {}",
                    rideId
            );

            return;
        }

        log.info(
                "Processing ride request: {}",
                rideId
        );

        matchingService.matchDriverForRide(event);

        idempotencyService.markProcessed(
                rideId,
                EntityType.RIDE_REQUESTED
        );

        log.info(
                "Ride request processed successfully: {}",
                rideId
        );
    }
}