package com.rideshare.rideservice.service;

import com.rideshare.rideservice.event.OutboxEvent;
import com.rideshare.rideservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {

        List<OutboxEvent> events =
                outboxEventRepository
                        .findByStatusOrderByCreatedAtAsc("PENDING");

        for (OutboxEvent event : events) {

            publishEvent(event);
        }
    }

    private void publishEvent(OutboxEvent event) {

        kafkaTemplate.send(
                event.getTopic(),
                event.getAggregateId(),
                event.getPayload()
        ).whenComplete((result, exception) -> {

            if (exception == null) {

                event.setStatus("SENT");

                outboxEventRepository.save(event);

                log.info(
                        "Outbox event {} published successfully",
                        event.getId()
                );

            } else {

                log.error(
                        "Failed to publish outbox event {}",
                        event.getId(),
                        exception
                );
            }
        });
    }
}
