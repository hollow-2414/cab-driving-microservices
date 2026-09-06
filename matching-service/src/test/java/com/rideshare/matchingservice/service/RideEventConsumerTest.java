package com.rideshare.matchingservice.service;

import com.rideshare.matchingservice.event.RideRequestedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RideEventConsumerTest {

    @Mock
    private MatchingService matchingService;

    @InjectMocks
    private RideEventConsumer rideEventConsumer;

    @Test
    @DisplayName("consumeRideRequestedEvent - Delegates event to MatchingService")
    void testConsumeRideRequestedEvent_Success() {
        RideRequestedEvent event = new RideRequestedEvent(
                "rider-1", "ride-1", 12.9716, 77.5946, "Pickup Address", 12.9352, 77.6245, "Drop Address"
        );

        rideEventConsumer.consumeRideRequestedEvent(event);

        verify(matchingService, times(1)).matchDriverForRide(event);
    }

    @Test
    @DisplayName("consumeRideRequestedEvent - Handles exception gracefully without propagating failure")
    void testConsumeRideRequestedEvent_ExceptionHandled() {
        RideRequestedEvent event = new RideRequestedEvent(
                "rider-1", "ride-1", 12.9716, 77.5946, "Pickup Address", 12.9352, 77.6245, "Drop Address"
        );

        doThrow(new RuntimeException("Location service unavailable"))
                .when(matchingService).matchDriverForRide(event);

        // Should not throw exception
        rideEventConsumer.consumeRideRequestedEvent(event);

        verify(matchingService, times(1)).matchDriverForRide(event);
    }
}
