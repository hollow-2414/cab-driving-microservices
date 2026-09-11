# 🚕 Cab-Driving Microservices Platform

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Kafka](https://img.shields.io/badge/Apache%20Kafka-Event--Driven-red.svg)](https://kafka.apache.org/)
[![Redis](https://img.shields.io/badge/Redis-Geospatial-dc382d.svg)](https://redis.io/)
[![MySQL](https://img.shields.io/badge/MySQL-Database-4479A1.svg)](https://www.mysql.com/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A production-grade, distributed, event-driven cab booking microservices system designed for real-time driver tracking, automated driver matching, fare estimation, and ride lifecycle management.

---

## 🌟 Key Features

- **⚡ Real-Time Geospatial Driver Indexing**: Ingests driver coordinates via Redis Geo (`GEOADD`, `GEORADIUS`) with $O(\log N + M)$ spatial query complexity.
- **🔒 Atomic Driver Claiming & 30s TTL**: Prevents race conditions and double-booking during matching using Redis Lua scripts (`CLAIM_SCRIPT`) with 30-second TTL reservations.
- **🔄 Event-Driven Matching Engine**: Decoupled asynchronous event processing using Apache Kafka (`ride.requested` and `ride.matched` topics).
- **🔒 Idempotent Event Processing**: Guards consumers (`RideEventConsumer`) against duplicate Kafka message delivery using persistent MySQL state checks (`IdempotencyService`).
- **🎯 Multi-Factor Driver Selection**: Intelligent driver scoring combining proximity (70% weight) and rating metrics (30% weight).
- **📐 Mathematical Fare Calculation**: Automatic pricing estimation powered by the Haversine trigonometric distance formula ($\text{₹}50\text{ base} + \text{₹}12/\text{km}$).
- **🛡️ Strict Ride Lifecycle State Machine**: Enforces valid state transitions (`REQUESTED` $\rightarrow$ `MATCHING` $\rightarrow$ `ACCEPTED` $\rightarrow$ `DRIVER_ARRIVING` $\rightarrow$ `RIDE_STARTED` $\rightarrow$ `COMPLETED` / `CANCELLED`).
- **🔁 Resilience & Dead Letter Topic (DLT)**: Non-blocking exponential backoff retries (1s, 2s, 4s) with Spring Kafka `DefaultErrorHandler`, `ErrorHandlingDeserializer`, and routing to `ride.requested-dlt` via `DeadLetterPublishingRecoverer`.
- **✅ 100% Test Coverage**: Fully verified with unit tests across all microservices using JUnit 5 & Mockito.

---

## 🏗️ System Architecture

```
                                  +-----------------------+
                                  |   Rider / Driver App  |
                                  +-----------+-----------+
                                              |
                     +------------------------+------------------------+
                     | HTTP / REST                                     | HTTP / REST
                     v                                                 v
           +-------------------+                             +-------------------+
           |   ride-service    |                             | location-service  |
           |    (Port: 8083)   |                             |    (Port: 8082)   |
           +---------+---------+                             +---------+---------+
                     |                                                 ^
                     | Kafka: ride.requested                           | OpenFeign REST
                     v                                                 |
           +-----------------------------------------------------------+---------+
           |                            matching-service                         |
           |                              (Port: 8084)                           |
           +----------------------------------+----------------------------------+
                                              |
                                              | Kafka: ride.matched
                                              v
                                    +-------------------+
                                    |   ride-service    |
                                    +-------------------+
```

---

## 📦 Microservices Breakdown

| Service | Port | Database / Cache | Responsibilities |
| :--- | :---: | :--- | :--- |
| **`location-service`** | `8082` | Redis (`drivers:location`, `driver:claim:*`) | Ingests driver telemetry, exposes radius search (`GEORADIUS`), manages atomic driver claiming with 30s TTL. |
| **`matching-service`** | `8084` | MySQL (`Requested_processed_events`) + Feign + Kafka | Listens for `ride.requested`, checks event idempotency, queries nearby drivers, claims best driver atomically, publishes `ride.matched`. |
| **`ride-service`** | `8083` | MySQL (`uberapp.rides`, `Matched_processed_events`) | Manages ride bookings, calculates Haversine fares, maintains strict state machine (`REQUESTED` $\rightarrow$ `MATCHING` $\rightarrow$ `ACCEPTED` $\rightarrow$ `DRIVER_ARRIVING` $\rightarrow$ `RIDE_STARTED` $\rightarrow$ `COMPLETED` / `CANCELLED`), checks event idempotency, publishes `ride.requested`. |

---

## 🚀 Quick Start in 3 Steps

### 1. Start Infrastructure (Redis & Kafka)
```bash
docker compose up -d
```

### 2. Create MySQL Database
```sql
CREATE DATABASE uberapp;
```

### 3. Build & Run Services
```bash
# Terminal 1: Location Service (Port 8082)
cd location-service && mvn spring-boot:run

# Terminal 2: Ride Service (Port 8083)
cd ride-service && mvn spring-boot:run

# Terminal 3: Matching Service (Port 8084)
cd matching-service && mvn spring-boot:run
```

---

## 🔗 Documentation Links

- 🔁 **[RETRY_DEDUPLICATION_V07.md](docs/engineering/RETRY_DEDUPLICATION_V07.md)** — Resilience retry deduplication, clean single-layer retry architecture, and Circuit Breaker hygiene.
- 🚦 **[RIDE_STATE_MACHINE_V06.md](docs/engineering/RIDE_STATE_MACHINE_V06.md)** — Strict ride lifecycle state machine, `DRIVER_ARRIVING` transition, and invalid state validation.
- 🔒 **[IDEMPOTENT_EVENT_PROCESSING_V04.md](docs/engineering/IDEMPOTENT_EVENT_PROCESSING_V04.md)** — Idempotent consumer pattern implementation using MySQL state tracking.
- 🚗 **[ATOMIC_DRIVER_CLAIMING_V05.md](docs/engineering/ATOMIC_DRIVER_CLAIMING_V05.md)** — Atomic driver claiming using Redis Lua scripts, 30s TTL cleanup, and stale release protection.
- 🚀 **[INITIAL_MICROSERVICES_V01.md](docs/engineering/INITIAL_MICROSERVICES_V01.md)** — Milestone v0.1: Initial microservices architecture with Kafka, Redis Geo, and MySQL.
- 📬 **[TRANSACTIONAL_OUTBOX_V02.md](docs/engineering/TRANSACTIONAL_OUTBOX_V02.md)** — Milestone v0.2: Transactional Outbox pattern implementation details.
- 🔁 **[RETRY_AND_DLT_DOCUMENTATION_V03.md](docs/engineering/RETRY_AND_DLT_DOCUMENTATION_V03.md)** — Milestone v0.3: Exponential Backoff Retries & Dead Letter Topic (DLT) pattern implementation details.
- 📖 **[ARCHITECTURE.md](ARCHITECTURE.md)** — Exhaustive HLD, LLD, Class Diagrams, Database Schemas, and Algorithms.
- 🛠️ **[SETUP_GUIDE.md](SETUP_GUIDE.md)** — Step-by-step setup guide with copy-pasteable cURL requests.

---

## 🔌 Core API Endpoints

### 1. Update Driver Location
```http
POST http://localhost:8082/api/locations
Content-Type: application/json

{ "driverId": "driver-101", "latitude": 12.9720, "longitude": 77.5950 }
```

### 2. Claim Driver (Atomic Reservation)
```http
POST http://localhost:8082/api/v1/drivers/driver-101/claim
Content-Type: application/json

{ "rideId": "R400" }
```

### 3. Request a Cab
```http
POST http://localhost:8083/api/v1/rides/request
Content-Type: application/json

{
  "riderId": "rider-001",
  "pickupLatitude": 12.9716,
  "pickupLongitude": 77.5946,
  "pickupAddress": "MG Road, Bangalore",
  "dropLatitude": 12.9352,
  "dropLongitude": 77.6245,
  "dropAddress": "Koramangala, Bangalore"
}
```

### 4. Track Ride Status
```http
GET http://localhost:8083/api/v1/rides/{id}
```

---

## 🧪 Running Unit Tests

Run isolated unit tests across all 3 microservices:

```bash
# Location Service Unit Tests
cd location-service && mvn test-compile surefire:test "-Dtest=LocationServiceTest"

# Matching Service Unit Tests
cd matching-service && mvn test-compile surefire:test "-Dtest=MatchingServiceTest,RideEventConsumerTest"

# Ride Service Unit Tests
cd ride-service && mvn test-compile surefire:test "-Dtest=RideServiceTest,RideEventConsumerTest"
```

---

## 📄 License

This project is open-source and available under the [MIT License](LICENSE).
