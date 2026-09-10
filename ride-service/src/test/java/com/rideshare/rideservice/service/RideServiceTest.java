package com.rideshare.rideservice.service;

import com.rideshare.rideservice.dto.RideRequest;
import com.rideshare.rideservice.dto.RideResponse;
import com.rideshare.rideservice.event.RideRequestedEvent;
import com.rideshare.rideservice.model.Ride;
import com.rideshare.rideservice.model.RideStatus;
import com.rideshare.rideservice.repository.RideRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RideServiceTest {

    @Mock
    private RideRepository rideRepository;

    @Mock
    private KafkaTemplate<String, RideRequestedEvent> kafkaTemplate;

    @InjectMocks
    private RideService rideService;

    @Test
    @DisplayName("requestRide - Saves ride, calculates estimated fare, publishes Kafka event, and updates status to MATCHING")
    void testRequestRide_Success() {
        RideRequest request = new RideRequest(
                "rider-1", 12.9716, 77.5946, "MG Road", 12.9352, 77.6245, "Koramangala");

        Ride mockRide = new Ride();
        mockRide.setId("ride-123");
        mockRide.setRiderId("rider-1");
        mockRide.setPickupLatitude(12.9716);
        mockRide.setPickupLongitude(77.5946);
        mockRide.setPickupAddress("MG Road");
        mockRide.setDropLatitude(12.9352);
        mockRide.setDropLongitude(77.6245);
        mockRide.setDropAddress("Koramangala");
        mockRide.setStatus(RideStatus.REQUESTED);
        mockRide.setEstimatedFare(100.0);

        when(rideRepository.save(any(Ride.class))).thenReturn(mockRide);

        RideResponse response = rideService.requestRide(request);

        assertNotNull(response);
        assertEquals("ride-123", response.getId());
        assertEquals("rider-1", response.getRiderId());
        assertEquals(RideStatus.MATCHING, response.getStatus());
        assertTrue(response.getEstimatedFare() > 50.0);

        verify(kafkaTemplate, times(1)).send(eq("ride.requested"), eq("ride-123"), any(RideRequestedEvent.class));
        verify(rideRepository, times(2)).save(any(Ride.class));
    }

    @Test
    @DisplayName("updateRideWithDriver - Sets driver ID and transitions status to ACCEPTED")
    void testUpdateRideWithDriver_Success() {
        Ride ride = new Ride();
        ride.setId("ride-123");
        ride.setStatus(RideStatus.MATCHING);

        when(rideRepository.findById("ride-123")).thenReturn(Optional.of(ride));

        rideService.updateRideWithDriver("ride-123", "driver-456");

        assertEquals("driver-456", ride.getDriverId());
        assertEquals(RideStatus.ACCEPTED, ride.getStatus());
        verify(rideRepository, times(1)).save(ride);
    }

    @Test
    @DisplayName("updateRideWithDriver - Throws exception when ride not found")
    void testUpdateRideWithDriver_NotFound() {
        when(rideRepository.findById("non-existent")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> rideService.updateRideWithDriver("non-existent", "driver-1"));
    }

    @Test
    @DisplayName("startRide - Transitions ACCEPTED ride to RIDE_STARTED and sets startedAt timestamp")
    void testStartRide_Success() {
        Ride ride = new Ride();
        ride.setId("ride-123");
        ride.setStatus(RideStatus.ACCEPTED);

        when(rideRepository.findById("ride-123")).thenReturn(Optional.of(ride));
        when(rideRepository.save(any(Ride.class))).thenReturn(ride);

        RideResponse response = rideService.startRide("ride-123");

        assertEquals(RideStatus.RIDE_STARTED, response.getStatus());
        assertNotNull(ride.getStartedAt());
        verify(rideRepository, times(1)).save(ride);
    }

    @Test
    @DisplayName("startRide - Throws exception if ride status is not ACCEPTED")
    void testStartRide_InvalidStatus() {
        Ride ride = new Ride();
        ride.setId("ride-123");
        ride.setStatus(RideStatus.REQUESTED);

        when(rideRepository.findById("ride-123")).thenReturn(Optional.of(ride));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> rideService.startRide("ride-123"));
        assertTrue(exception.getMessage().contains("Ride cannot be started"));
    }

    @Test
    @DisplayName("completeRide - Transitions RIDE_STARTED ride to COMPLETED and sets actualFare")
    void testCompleteRide_Success() {
        Ride ride = new Ride();
        ride.setId("ride-123");
        ride.setStatus(RideStatus.RIDE_STARTED);
        ride.setEstimatedFare(120.50);

        when(rideRepository.findById("ride-123")).thenReturn(Optional.of(ride));
        when(rideRepository.save(any(Ride.class))).thenReturn(ride);

        RideResponse response = rideService.completeRide("ride-123");

        assertEquals(RideStatus.COMPLETED, response.getStatus());
        assertEquals(120.50, response.getActualFare());
        assertNotNull(ride.getCompletedAt());
        verify(rideRepository, times(1)).save(ride);
    }

    @Test
    @DisplayName("completeRide - Throws exception if ride status is not RIDE_STARTED")
    void testCompleteRide_InvalidStatus() {
        Ride ride = new Ride();
        ride.setId("ride-123");
        ride.setStatus(RideStatus.ACCEPTED);

        when(rideRepository.findById("ride-123")).thenReturn(Optional.of(ride));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> rideService.completeRide("ride-123"));
        assertTrue(exception.getMessage().contains("Ride cannot be completed"));
    }

    @Test
    @DisplayName("cancelRide - Transitions ride status to CANCELLED")
    void testCancelRide_Success() {
        Ride ride = new Ride();
        ride.setId("ride-123");
        ride.setStatus(RideStatus.MATCHING);

        when(rideRepository.findById("ride-123")).thenReturn(Optional.of(ride));
        when(rideRepository.save(any(Ride.class))).thenReturn(ride);

        RideResponse response = rideService.cancelRide("ride-123");

        assertEquals(RideStatus.CANCELLED, response.getStatus());
        verify(rideRepository, times(1)).save(ride);
    }

    @Test
    @DisplayName("getRideById - Returns ride response for existing ride")
    void testGetRideById_Success() {
        Ride ride = new Ride();
        ride.setId("ride-123");
        ride.setRiderId("rider-1");
        ride.setStatus(RideStatus.ACCEPTED);

        when(rideRepository.findById("ride-123")).thenReturn(Optional.of(ride));

        RideResponse response = rideService.getRideById("ride-123");

        assertEquals("ride-123", response.getId());
        assertEquals("rider-1", response.getRiderId());
    }

    @Test
    @DisplayName("getRidesByRider - Returns list of ride responses for a rider")
    void testGetRidesByRider_Success() {
        Ride ride1 = new Ride();
        ride1.setId("ride-1");
        ride1.setRiderId("rider-1");

        Ride ride2 = new Ride();
        ride2.setId("ride-2");
        ride2.setRiderId("rider-1");

        when(rideRepository.findByRiderIdOrderByCreatedAtDesc("rider-1"))
                .thenReturn(List.of(ride1, ride2));

        List<RideResponse> responses = rideService.getRidesByRider("rider-1");

        assertEquals(2, responses.size());
        assertEquals("ride-1", responses.get(0).getId());
        assertEquals("ride-2", responses.get(1).getId());
    }
}
