package com.rideshare.rideservice.repository;

import com.rideshare.rideservice.event.ProcessedEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository
        extends JpaRepository<ProcessedEntity, String> {
}
