package com.rideshare.matchingservice.service;

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

        log.error("Processing ride request: {}", event.getRideId());

        matchingService.matchDriverForRide(event);

    }
}