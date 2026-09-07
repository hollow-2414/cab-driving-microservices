package com.rideshare.rideservice.event;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_events")
@Data
@NoArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String topic;

    private String eventType;

    private String aggregateId;

    @Column(columnDefinition = "TEXT")
    private String payload;

    private String status;

    private LocalDateTime createdAt;

    public OutboxEvent(
            String topic,
            String eventType,
            String aggregateId,
            String payload,
            String status,
            LocalDateTime createdAt
    ) {
        this.topic = topic;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.status = status;
        this.createdAt = createdAt;
    }
}
