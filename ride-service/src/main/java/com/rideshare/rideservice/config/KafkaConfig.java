package com.rideshare.rideservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    // Topic where Ride Service published ride request
    // Matching Service subcribers to this topic

    @Bean
    public NewTopic rideRequestedTopic() {
        return TopicBuilder.name("ride.requested")
                .partitions(3)
                .replicas(1)
                .build();
    }

    // Topic where Matching Service publishes match results
    // Ride Service subscribers to this topic

    @Bean
    public NewTopic rideMatchedTopic() {
        return TopicBuilder.name("ride.matched")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic rideRequestedDltTopic() {
        return TopicBuilder.name("ride.requested-dlt")
                .partitions(3)
                .replicas(1)
                .build();
    }

}
