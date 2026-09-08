# 🔒 Idempotent Event Processing

> **TL;DR**: Prevents duplicate Kafka message processing across `matching-service` and `ride-service` by persisting processed event IDs in MySQL.

---

## ⚡ How It Works

1. **Check**: `RideEventConsumer` calls `idempotencyService.isProcessed(rideId)`.
2. **Skip**: If `true`, the event is logged and ignored.
3. **Execute & Mark**: If `false`, business logic runs, and `idempotencyService.markProcessed(rideId, ...)` saves the event ID in MySQL.

```
Kafka Event ──► Consumer ──► Is Processed in DB? ──┬── YES ──► Log & Skip
                                                   └── NO  ──► Process Logic ──► Save to DB
```

---

## 💾 Storage

- **`matching-service`**: `Requested_processed_events` table ([`ProcessedEvent.java`](../../matching-service/src/main/java/com/rideshare/matchingservice/entity/ProcessedEvent.java))
- **`ride-service`**: `Matched_processed_events` table ([`ProcessedEntity.java`](../../ride-service/src/main/java/com/rideshare/rideservice/event/ProcessedEntity.java))

---

## 📁 Key Files

- 🛠️ **Matching Service**: [`IdempotencyService.java`](../../matching-service/src/main/java/com/rideshare/matchingservice/service/IdempotencyService.java) \| [`RideEventConsumer.java`](../../matching-service/src/main/java/com/rideshare/matchingservice/service/RideEventConsumer.java)
- 🛠️ **Ride Service**: [`IdempotencyService.java`](../../ride-service/src/main/java/com/rideshare/rideservice/service/IdempotencyService.java) \| [`RideEventConsumer.java`](../../ride-service/src/main/java/com/rideshare/rideservice/service/RideEventConsumer.java)
