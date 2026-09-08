package com.rideshare.rideservice.service;


import com.rideshare.rideservice.event.RideMatchedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class RideEventConsumer {

    private final RideService rideService;
    private final IdempotencyService idempotencyService;

    @KafkaListener(
            topics = "ride.matched",
            groupId = "ride-service-group"
    )
    public void consumeRideMatchedEvent(RideMatchedEvent event) {

        log.info("🔥 RIDE MATCHED LISTENER CALLED: {}", event);

        String rideId = event.getRideId();

        if (idempotencyService.isProcessed(
                rideId
        )) {

            log.info(
                    "Ignoring duplicate ride matched event: {}",
                    rideId
            );

            return;
        }

        log.info(
                "Processing ride matched event: {}",
                rideId
        );

        rideService.updateRideWithDriver(
                rideId,
                event.getDriverId()
        );

        idempotencyService.markProcessed(
                rideId,
                "RIDE_MATCHED"
        );

        log.info(
                "Ride matched event processed successfully: {}",
                rideId
        );
    }
}
