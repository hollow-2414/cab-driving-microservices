package com.rideshare.matchingservice.service;

import com.rideshare.matchingservice.client.LocationServiceClient;
import com.rideshare.matchingservice.dto.NearByDriverResponse;
import com.rideshare.matchingservice.event.RideMatchedEvent;
import com.rideshare.matchingservice.event.RideRequestedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    @Mock
    private LocationServiceClient locationServiceClient;

    @Mock
    private KafkaTemplate<String, RideMatchedEvent> kafkaTemplate;

    @InjectMocks
    private MatchingService matchingService;

    @Test
    @DisplayName("matchDriverForRide - Matches best driver and publishes RideMatchedEvent to Kafka")
    void testMatchDriverForRide_Success() {
        RideRequestedEvent requestedEvent = new RideRequestedEvent(
                "rider-50", "ride-100", 12.9716, 77.5946, "Pickup Loc", 12.9352, 77.6245, "Drop Loc"
        );

        NearByDriverResponse driver1 = new NearByDriverResponse("driver-1", 12.9720, 77.5950, 0.5);
        when(locationServiceClient.getNearByDrivers(eq(12.9716), eq(77.5946), eq(5.0)))
                .thenReturn(List.of(driver1));

        matchingService.matchDriverForRide(requestedEvent);

        ArgumentCaptor<RideMatchedEvent> eventCaptor = ArgumentCaptor.forClass(RideMatchedEvent.class);
        verify(kafkaTemplate, times(1)).send(eq("ride.matched"), eq("ride-100"), eventCaptor.capture());

        RideMatchedEvent publishedEvent = eventCaptor.getValue();
        assertEquals("ride-100", publishedEvent.getRideId());
        assertEquals("rider-50", publishedEvent.getRiderId());
        assertEquals("driver-1", publishedEvent.getDriverId());
        assertEquals(12.9720, publishedEvent.getDriverLatitude());
        assertEquals(77.5950, publishedEvent.getDriverLongitude());
        assertEquals(0.5, publishedEvent.getDistanceToPickupKm());
    }

    @Test
    @DisplayName("matchDriverForRide - Handles empty nearby driver list without publishing")
    void testMatchDriverForRide_NoDriversFound() {
        RideRequestedEvent requestedEvent = new RideRequestedEvent(
                "rider-50", "ride-101", 12.9716, 77.5946, "Pickup Loc", 12.9352, 77.6245, "Drop Loc"
        );

        when(locationServiceClient.getNearByDrivers(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Collections.emptyList());

        matchingService.matchDriverForRide(requestedEvent);

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("matchDriverForRide - Selects candidate among multiple nearby drivers")
    void testMatchDriverForRide_MultipleDrivers() {
        RideRequestedEvent requestedEvent = new RideRequestedEvent(
                "rider-50", "ride-102", 12.9716, 77.5946, "Pickup Loc", 12.9352, 77.6245, "Drop Loc"
        );

        NearByDriverResponse driver1 = new NearByDriverResponse("driver-close", 12.9718, 77.5948, 0.1);
        NearByDriverResponse driver2 = new NearByDriverResponse("driver-far", 12.9900, 77.6100, 4.5);

        when(locationServiceClient.getNearByDrivers(eq(12.9716), eq(77.5946), eq(5.0)))
                .thenReturn(List.of(driver1, driver2));

        matchingService.matchDriverForRide(requestedEvent);

        verify(kafkaTemplate, times(1)).send(eq("ride.matched"), eq("ride-102"), any(RideMatchedEvent.class));
    }
}
