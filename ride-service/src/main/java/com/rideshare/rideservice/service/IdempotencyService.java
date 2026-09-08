package com.rideshare.rideservice.service;

import com.rideshare.rideservice.event.ProcessedEntity;
import com.rideshare.rideservice.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final ProcessedEventRepository processedEventRepository;

    public boolean isProcessed(String eventId) {
        return processedEventRepository.existsById(eventId);
    }

    public void markProcessed(String eventId, String entityType) {

        ProcessedEntity processedEvent = new ProcessedEntity(
                eventId,
                entityType,
                LocalDateTime.now()
        );

        processedEventRepository.save(processedEvent);
    }
}
