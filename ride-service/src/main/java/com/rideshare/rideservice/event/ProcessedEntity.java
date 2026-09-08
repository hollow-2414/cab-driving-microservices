package com.rideshare.rideservice.event;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name ="Matched_processed_events")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProcessedEntity {

    @Id
    private String eventId;

    private String entityType;

    private LocalDateTime localDateTime;
}

