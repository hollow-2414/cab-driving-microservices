Initial Microservices Platform (Kafka & Redis)

Milestone: v0.1
Services: location-service, ride-service, matching-service

Overview

Initial architecture for an event-driven, distributed cab booking microservices system using Spring Boot, Apache Kafka, Redis Geo, and MySQL.

System Architecture

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

Microservices Breakdown

1. location-service (Port 8082)
   - Data Store: Redis (Geospatial Indexing `drivers:location`)
   - Function: Ingests driver location heartbeats (`POST /api/locations`) and exposes radius search API (`GET /api/locations/nearby`).

2. ride-service (Port 8083)
   - Data Store: MySQL (`uberapp.rides`)
   - Function: Manages ride lifecycle (`REQUESTED` → `MATCHING` → `ACCEPTED` → `RIDE_STARTED` → `COMPLETED` / `CANCELLED`).
   - Calculates fare via Haversine distance formula (Base ₹50 + ₹12/km).
   - Publishes `RideRequestedEvent` to Kafka topic `ride.requested`.
   - Consumes `RideMatchedEvent` from Kafka topic `ride.matched` to assign driver.

3. matching-service (Port 8084)
   - Data Store: Stateless (Uses OpenFeign + Kafka)
   - Function: Listens to `ride.requested` event, queries `location-service` via Feign for nearby drivers.
   - Calculates candidate score using 70% proximity weight + 30% rating weight.
   - Publishes `RideMatchedEvent` to Kafka topic `ride.matched`.

End-to-End Event Flow

Rider Request (POST /api/rides)
    ↓
ride-service creates Ride in MySQL (Status: MATCHING)
    ↓
ride-service publishes RideRequestedEvent to Kafka ("ride.requested")
    ↓
matching-service consumes RideRequestedEvent
    ↓
matching-service calls location-service via Feign to get nearby drivers
    ↓
matching-service scores drivers & picks best match
    ↓
matching-service publishes RideMatchedEvent to Kafka ("ride.matched")
    ↓
ride-service consumes RideMatchedEvent & updates Ride (Status: ACCEPTED, DriverId assigned)

Key Technologies

- Spring Boot 3.x & Java 21
- Apache Kafka (Inter-service Event Streaming)
- Redis Geo (`GEOADD`, `GEORADIUS` for spatial indexing)
- MySQL (Relational persistence for ride state)
- Spring Cloud OpenFeign (Declarative HTTP client)
