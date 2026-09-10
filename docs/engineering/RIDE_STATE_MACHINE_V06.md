# 🚦 Strict Ride Lifecycle State Machine

> **TL;DR**: Enforces strict, validated state transitions in `ride-service` for the ride lifecycle while keeping driver claiming decoupled in `location-service`.

---

## ⚡ How It Works

1. **Transition Validation**: `RideStateTransitionValidator.java` intercepts all state changes and throws `IllegalStateException` on invalid state skips or transitions out of terminal states (`COMPLETED`, `CANCELLED`).
2. **Async Driver Match**: Rides start as `REQUESTED` → `MATCHING`. `matching-service` asynchronously matches a driver via Kafka and updates status to `ACCEPTED`.
3. **Driver Arrival Step**: Added `DRIVER_ARRIVING` transition (`PUT /api/v1/rides/{id}/arriving`) between driver acceptance and ride start.

```
REQUESTED ──► MATCHING ──► ACCEPTED ──► DRIVER_ARRIVING ──► RIDE_STARTED ──► COMPLETED (Terminal)
    │             │            │               │                │
    └─────────────┴────────────┴───────────────┴────────────────┴──────► CANCELLED (Terminal)
```

---

## 🌐 API Endpoints

- **PUT** `/api/v1/rides/{id}/arriving` $\rightarrow$ `ACCEPTED` $\rightarrow$ `DRIVER_ARRIVING`
- **PUT** `/api/v1/rides/{id}/start` $\rightarrow$ `DRIVER_ARRIVING` $\rightarrow$ `RIDE_STARTED`
- **PUT** `/api/v1/rides/{id}/complete` $\rightarrow$ `RIDE_STARTED` $\rightarrow$ `COMPLETED`
- **PUT** `/api/v1/rides/{id}/cancel` $\rightarrow$ Non-terminal state $\rightarrow$ `CANCELLED`

---

## 📁 Key Files

- 🛡️ **Validator**: [`RideStateTransitionValidator.java`](../../ride-service/src/main/java/com/rideshare/rideservice/service/RideStateTransitionValidator.java)
- 🚦 **Enum**: [`RideStatus.java`](../../ride-service/src/main/java/com/rideshare/rideservice/model/RideStatus.java)
- 🛠️ **Service**: [`RideService.java`](../../ride-service/src/main/java/com/rideshare/rideservice/service/RideService.java)
- 🌐 **Controller**: [`RideController.java`](../../ride-service/src/main/java/com/rideshare/rideservice/controller/RideController.java)
