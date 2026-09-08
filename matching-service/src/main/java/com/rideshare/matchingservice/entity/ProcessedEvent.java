package com.rideshare.matchingservice.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name ="Requested_processed_events")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProcessedEvent {

    @Id
    private String eventId;

    @Enumerated(EnumType.STRING)
    private EntityType eventType;

    private LocalDateTime localDateTime;
}
