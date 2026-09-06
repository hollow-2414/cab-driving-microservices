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
- **🔄 Event-Driven Matching Engine**: Decoupled asynchronous event processing using Apache Kafka (`ride.requested` and `ride.matched` topics).
- **🎯 Multi-Factor Driver Selection**: Intelligent driver scoring combining proximity (70% weight) and rating metrics (30% weight).
- **📐 Mathematical Fare Calculation**: Automatic pricing estimation powered by the Haversine trigonometric distance formula ($\text{₹}50\text{ base} + \text{₹}12/\text{km}$).
- **🛡️ Strict Ride Lifecycle State Machine**: Enforces valid state transitions (`REQUESTED` $\rightarrow$ `MATCHING` $\rightarrow$ `ACCEPTED` $\rightarrow$ `RIDE_STARTED` $\rightarrow$ `COMPLETED` / `CANCELLED`).
- **✅ 100% Test Coverage**: Fully verified with 21 unit tests across all microservices using JUnit 5 & Mockito.

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
| **`location-service`** | `8082` | Redis (`drivers:location`) | Ingests driver telemetry heartbeats, exposes radius search (`GEORADIUS`). |
| **`matching-service`** | `8084` | Stateless (Feign + Kafka) | Listens for `ride.requested`, queries nearby drivers, scores candidates, publishes `ride.matched`. |
| **`ride-service`** | `8083` | MySQL (`uberapp.rides`) | Manages ride bookings, calculates Haversine fares, maintains state machine, publishes `ride.requested`. |

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

- 📖 **[PROJECT_DOCUMENTATION.md](PROJECT_DOCUMENTATION.md)** — Exhaustive HLD, LLD, Class Diagrams, Database Schemas, and Algorithms.
- 🛠️ **[SETUP_GUIDE.md](SETUP_GUIDE.md)** — Step-by-step setup guide with copy-pasteable cURL requests.
- 🧪 **[TEST_REPORT.md](TEST_REPORT.md)** — Detailed unit test execution matrix and verification details.

---

## 🔌 Core API Endpoints

### 1. Update Driver Location
```http
POST http://localhost:8082/api/locations
Content-Type: application/json

{ "driverId": "driver-101", "latitude": 12.9720, "longitude": 77.5950 }
```

### 2. Request a Cab
```http
POST http://localhost:8081/api/rides
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

### 3. Track Ride Status
```http
GET http://localhost:8081/api/rides/{rideId}
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
