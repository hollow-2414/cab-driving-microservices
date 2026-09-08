# Retry and Dead Letter Topic (DLT) Architecture & Implementation Guide

This document provides a detailed overview of the **Retry** and **Dead Letter Topic (DLT)** pattern implemented across the `ride-service` and `matching-service` microservices.

---

## 1. Overview & Architecture

In an event-driven architecture using Apache Kafka, failures during event consumption can occur due to:
1. **Transient failures**: Temporary database connection issues, network timeouts, or lock contention.
2. **Poison Pill messages**: Malformed payloads, missing required fields, or deserialization errors that can never be successfully processed.

Without proper error handling, a failing message can either crash the consumer or get stuck in an infinite retry loop, blocking the partition for other messages.

### Solution Architecture

```
                                  +-----------------------+
                                  |  Ride Service         |
                                  |  (Outbox Publisher)   |
                                  +-----------+-----------+
                                              |
                                              v (ride.requested)
                                  +-----------+-----------+
                                  |   Kafka Broker        |
                                  +-----------+-----------+
                                              |
                                              v
                              +---------------+---------------+
                              | Matching Service              |
                              | (RideEventConsumer)           |
                              +---------------+---------------+
                                              |
                     +------------------------+------------------------+
                     | (Success)                                       | (Failure)
                     v                                                 v
        +------------+-----------+                        +------------+-----------+
        | Processed & Matched Driver|                        | DefaultErrorHandler       |
        +------------------------+                        +------------+-----------+
                                                                       |
                                                +----------------------+----------------------+
                                                | Retries Exceeded / Deserialization Error   |
                                                v                                             v
                                  +-------------+-------------+                 +-------------+-------------+
                                  | Exponential Backoff Retry |                 | DeadLetterPublishingRecoverer|
                                  | Attempt 1: +1000ms        |                 +-------------+-------------+
                                  | Attempt 2: +2000ms        |                               |
                                  | Attempt 3: +4000ms        |                               v
                                  +---------------------------+                 +-------------+-------------+
                                                                                | Topic: ride.requested-dlt  |
                                                                                +---------------------------+
```

---

## 2. Component Updates Breakdown

### A. Producer Side (`ride-service`)

#### 1. DLT Topic Creation
* **File:** [`KafkaConfig.java`](../../ride-service/src/main/java/com/rideshare/rideservice/config/KafkaConfig.java)
* **Description:** Added a `NewTopic` bean for `ride.requested-dlt` with 3 partitions and replication factor 1 to ensure the DLT exists before consumers attempt to publish failure records.

```java
@Bean
public NewTopic rideRequestedDltTopic() {
    return TopicBuilder.name("ride.requested-dlt")
            .partitions(3)
            .replicas(1)
            .build();
}
```

#### 2. Typed Outbox Event Publishing
* **File:** [`OutboxPublisher.java`](../../ride-service/src/main/java/com/rideshare/rideservice/service/OutboxPublisher.java)
* **Description:** Updated `KafkaTemplate` from `KafkaTemplate<String, String>` to `KafkaTemplate<String, RideRequestedEvent>`. Outbox JSON payloads are deserialized into strongly-typed `RideRequestedEvent` objects before publishing.

---

### B. Consumer Side (`matching-service`)

#### 1. Safe Deserialization Strategy
* **File:** [`application.properties`](../../matching-service/src/main/resources/application.properties)
* **Description:** Replaced raw `JacksonJsonDeserializer` with `ErrorHandlingDeserializer`. If a record cannot be deserialized, `ErrorHandlingDeserializer` intercepts the exception and passes it cleanly to the `DefaultErrorHandler` instead of crashing the listener loop.

```properties
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
spring.kafka.consumer.properties.spring.deserializer.value.delegate.class=org.springframework.kafka.support.serializer.JacksonJsonDeserializer
```

#### 2. Kafka Listener Container & Retry Configuration
* **File:** [`KafkaConsumerConfig.java`](../../matching-service/src/main/java/com/rideshare/matchingservice/config/KafkaConsumerConfig.java)
* **Description:** 
  - **DLT Producer Template (`dltKafkaTemplate`):** Configured to publish records to Kafka when retries are exhausted.
  - **Recoverer (`DeadLetterPublishingRecoverer`):** Automatically routes failed records to `<original-topic>-dlt` (`ride.requested-dlt`).
  - **Exponential Backoff (`ExponentialBackOffWithMaxRetries`):**
    - Max Retries: `3`
    - Initial Interval: `1000ms` (1 second)
    - Multiplier: `2.0`
    - *Retry Timeline:* Attempt 1 (+1s) -> Attempt 2 (+2s) -> Attempt 3 (+4s) -> Route to DLT.
  - **Container Factory (`kafkaListenerContainerFactory`):** Binds `DefaultErrorHandler` into the consumer container.

```java
@Bean
public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
        KafkaTemplate<String, Object> dltKafkaTemplate) {
    return new DeadLetterPublishingRecoverer(dltKafkaTemplate);
}

@Bean
public DefaultErrorHandler kafkaErrorHandler(
        DeadLetterPublishingRecoverer recoverer) {
    ExponentialBackOffWithMaxRetries backOff =
            new ExponentialBackOffWithMaxRetries(3);
    backOff.setInitialInterval(1000L);
    backOff.setMultiplier(2.0);

    return new DefaultErrorHandler(recoverer, backOff);
}
```

#### 3. Consumer Exception Propagation
* **File:** [`RideEventConsumer.java`](../../matching-service/src/main/java/com/rideshare/matchingservice/service/RideEventConsumer.java)
* **Description:** Removed explicit `try-catch` block that was swallowing exceptions. Specified `containerFactory = "kafkaListenerContainerFactory"` on `@KafkaListener`. Uncaught exceptions now propagate to Spring Kafka's `DefaultErrorHandler`.

```java
@KafkaListener(
        topics = "ride.requested",
        groupId = "matching-service-group",
        containerFactory = "kafkaListenerContainerFactory"
)
public void consumeRideRequestedEvent(RideRequestedEvent event) {
    log.error("Processing ride request: {}", event.getRideId());
    matchingService.matchDriverForRide(event);
}
```

---

## 3. Summary of Failure Handling Scenarios

| Scenario | Consumer Behavior | Outcome / Destination |
| :--- | :--- | :--- |
| **Normal Processing** | Event processed cleanly | Driver assigned, `ride.matched` event published |
| **Transient Error** (e.g. DB locks) | Retried 3 times with exponential backoff (1s, 2s, 4s) | Recovers on subsequent retry or moves to DLT if max retries exceeded |
| **Poison Pill / Invalid JSON** | Captured by `ErrorHandlingDeserializer` | Fails fast, bypasses retries, sent to `ride.requested-dlt` |
| **Business Logic Failure** | Exception propagates out of consumer method | Retried up to 3 times, then sent to `ride.requested-dlt` |

---

## 4. DLT Record Headers Attached by Spring Kafka

When a message is sent to `ride.requested-dlt`, Spring Kafka enriches the record with diagnostic headers:

- `kafka_dlt-exception-message`: Error message details.
- `kafka_dlt-exception-stacktrace`: Full exception stack trace.
- `kafka_dlt-original-topic`: `ride.requested`
- `kafka_dlt-original-partition`: Partition ID of original message.
- `kafka_dlt-original-offset`: Offset in original topic.

---

## 5. Verification & Testing

1. **Testing Retries:**
   - Throw a `RuntimeException` inside `MatchingService.matchDriverForRide()`.
   - Send a ride request and observe consumer logs: you will see 3 retry attempts at 1s, 2s, and 4s intervals.

2. **Testing DLT Delivery:**
   - After the 3rd retry attempt fails, check logs for `DeadLetterPublishingRecoverer` publishing to `ride.requested-dlt`.
   - Consume from topic `ride.requested-dlt` using Kafka CLI or Kafka UI tool to verify message payload and headers.
