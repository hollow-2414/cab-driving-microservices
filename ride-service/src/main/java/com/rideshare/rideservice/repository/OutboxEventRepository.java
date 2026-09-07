package com.rideshare.rideservice.repository;

import com.rideshare.rideservice.event.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends
        JpaRepository<OutboxEvent,Long> {
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(
            String status
    );
}
