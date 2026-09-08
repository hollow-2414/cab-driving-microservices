package com.rideshare.matchingservice.service;

import com.rideshare.matchingservice.entity.EntityType;
import com.rideshare.matchingservice.entity.ProcessedEvent;
import com.rideshare.matchingservice.repository.ProcessedEventRepository;
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

    public void markProcessed(String eventId, EntityType entityType) {

        ProcessedEvent processedEvent = new ProcessedEvent(
                eventId,
                entityType,
                LocalDateTime.now()
        );

        processedEventRepository.save(processedEvent);
    }
}
