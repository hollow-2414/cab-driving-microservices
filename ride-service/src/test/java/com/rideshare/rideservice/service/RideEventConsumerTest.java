package com.rideshare.rideservice.service;

import com.rideshare.rideservice.event.RideMatchedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RideEventConsumerTest {

    @Mock
    private RideService rideService;

    @InjectMocks
    private RideEventConsumer rideEventConsumer;

    @Test
    @DisplayName("consumeRideMatchedEvent - Invokes updateRideWithDriver on RideService")
    void testConsumeRideMatchedEvent() {
        RideMatchedEvent event = new RideMatchedEvent("ride-123", "rider-1", "driver-456", 12.97, 77.59, 1.2);

        rideEventConsumer.consumeRideMatchedEvent(event);

        verify(rideService, times(1)).updateRideWithDriver("ride-123", "driver-456");
    }
}
