# 🚕 Cab-Driving Microservices Platform

## System Architecture & Technical Deep-Dive

> A distributed, event-driven ride-sharing platform built with **Java +
> Spring Boot + Kafka + Redis + MySQL**.

------------------------------------------------------------------------

## 📑 Table of Contents

1.  [System Overview](#1-system-overview)
2.  [Architecture](#2-architecture)
3.  [Infrastructure](#3-infrastructure)
4.  [Microservice Responsibilities](#4-microservice-responsibilities)
5.  [Communication Strategy](#5-communication-strategy)
6.  [Auth Service](#6-auth-service)
7.  [Location Service](#7-location-service)
8.  [Matching Service](#8-matching-service)
9.  [Ride Service](#9-ride-service)
10. [Kafka Event Contracts](#10-kafka-event-contracts)
11. [Ride Lifecycle](#11-ride-lifecycle)
12. [Core Algorithms](#12-core-algorithms)
13. [API Directory](#13-api-directory)
14. [End-to-End Flow](#14-end-to-end-flow)
15. [Failure & Reliability Considerations](#15-failure--reliability-considerations)
16. [Scalability Considerations](#16-scalability-considerations)

------------------------------------------------------------------------

# 1. System Overview

The Cab-Driving platform is a distributed microservices system designed
around four core capabilities:

- 🔐 **Authentication & Authorization**
- 📍 **Real-time driver location tracking**
- 🎯 **Automated driver-rider matching**
- 🚕 **Ride lifecycle management**

The system contains four Spring Boot microservices:

| Service            | Responsibility                                              |   Port |
|--------------------|-------------------------------------------------------------|-------:|
| `auth-service`     | JWT issuance, user registration/login, RBAC, service tokens | `8085` |
| `location-service` | Driver location + nearby-driver queries + atomic claiming   | `8082` |
| `ride-service`     | Ride lifecycle + persistence + fare calculation + ownership | `8083` |
| `matching-service` | Driver discovery + scoring + service-JWT-authenticated claim| `8084` |

Infrastructure:

| Component | Purpose                         | Host Port | Container Port |
|-----------|---------------------------------|----------:|---------------:|
| MySQL     | Ride & auth persistence         |    `3306` |         `3306` |
| Redis     | Driver geospatial data + claims |    `6380` |         `6379` |
| Kafka     | Event streaming                 |    `9093` |         `9092` |
| Zookeeper | Kafka coordination              |    `2182` |         `2181` |

> **Important:** The Spring Boot services run locally, while
> Redis/Kafka/Zookeeper run through Docker Compose. Therefore the Spring
> Boot applications connect to the **host ports** (`6380`, `9093`,
> etc.).

------------------------------------------------------------------------

# 2. Architecture

## 2.1 High-Level Architecture

``` text
                         ┌─────────────────────┐
                         │    Rider / Driver   │
                         │       Client        │
                         └──────────┬──────────┘
                                    │
              +-------JWT Auth-------+-------JWT Auth---------+
              |  (POST /auth/login)                           |
              v                                               v
    +------------------+                          +──────────────────+
    |   auth-service   |                          │   ride-service   │
    |     :8085        |                          │      :8083       │
    | MySQL: auth_db   |                          └────────┬─────────┘
    +------------------+                                   │
                                                           │ ride.requested
                                                           ▼
              +----------------------------------------------+--------+
              |                   matching-service                     |
              |                       :8084                            |
              +-----------------------------+--------------------------+
                                            |                  |
                        Kafka: ride.matched |                  | Feign + Service JWT
                                            v                  v
                                  +──────────────+   +──────────────────+
                                  │ ride-service │   │ location-service │
                                  └──────────────┘   │      :8082       │
                                                     │ Redis: Geo+Claim │
                                                     └──────────────────┘
```

### Security Architecture

``` text
                         Client (User)
                               │
                               │ Bearer JWT (sub: userId, role: RIDER/DRIVER)
                               ▼
                  ┌─────────────────────────┐
                  │      Ride Service       │
                  └────────────┬────────────┘
                               │
                      Spring Security
                               │
             ┌─────────────────┴─────────────────┐
             │                                   │
           RBAC                              Ownership
     ("Am I a RIDER?")                  ("Is this MY ride?")
             │                                   │
             └─────────────────┬─────────────────┘
                               │
                               ▼
                            Ride DB
─────────────────────────────────────────────────────────────
                 Service-to-Service Flow:

                 ┌──────────────────┐
                 │ Matching Service │
                 └──────────┬───────┘
                            │ Service JWT
                            │ (sub: matching-service, role: SERVICE)
                            ▼
                 ┌──────────────────┐
                 │ Location Service │──► @PreAuthorize("hasRole('SERVICE')")
                 └──────────────────┘
```

------------------------------------------------------------------------

## 2.2 Mermaid Architecture Diagram

``` mermaid
graph TD

    Rider[Rider Client]
    Driver[Driver Client]

    subgraph Services
        AuthService["auth-service :8085"]
        RideService["ride-service :8083"]
        LocationService["location-service :8082"]
        MatchingService["matching-service :8084"]
    end

    subgraph Infrastructure
        AuthDB[("MySQL auth_db :3306")]
        MySQL[("MySQL uberapp :3306")]
        Redis[("Redis\nhost :6380 → container :6379")]
        Kafka[["Kafka\nhost :9093 → container :9092"]]
        Zookeeper[["Zookeeper\nhost :2182 → container :2181"]]
    end

    Rider -->|"POST /auth/register + /auth/login"| AuthService
    Driver -->|"POST /auth/register + /auth/login"| AuthService
    AuthService -->|"Save user"| AuthDB

    Rider -->|"POST ride request + Bearer JWT"| RideService
    Driver -->|"Location heartbeat + Bearer JWT"| LocationService

    LocationService --> Redis

    RideService -->|"Save ride"| MySQL
    RideService -->|"ride.requested"| Kafka

    Kafka -->|"ride.requested"| MatchingService

    MatchingService -->|"Feign + Service JWT"| LocationService
    LocationService -->|"Geo search"| Redis
    LocationService -->|"Atomic Lua claim"| Redis

    MatchingService -->|"ride.matched"| Kafka
    Kafka -->|"ride.matched"| RideService

    Zookeeper --> Kafka
```

------------------------------------------------------------------------

# 3. Infrastructure

The Docker Compose infrastructure consists of **Redis, Zookeeper, and
Kafka**.

## 3.1 Redis

``` yaml
redis:
  image: redis:latest
  container_name: cab-driving-redis
  ports:
    - "6380:6379"
```

### Connection

``` text
Docker container: 6379
Host machine:    6380
```

Spring Boot services running locally therefore use:

``` properties
spring.data.redis.host=localhost
spring.data.redis.port=6380
```

Redis stores driver locations using Redis Geospatial functionality.

------------------------------------------------------------------------

## 3.2 Zookeeper

``` yaml
zookeeper:
  image: confluentinc/cp-zookeeper:7.4.0
  container_name: cab-driving-zookeeper
  ports:
    - "2182:2181"
  environment:
    ZOOKEEPER_CLIENT_PORT: 2181
    ZOOKEEPER_TICK_TIME: 2000
```

Zookeeper is used by the selected **Confluent Kafka 7.4.0** setup for
Kafka broker coordination.

------------------------------------------------------------------------

## 3.3 Kafka

``` yaml
kafka:
  image: confluentinc/cp-kafka:7.4.0
  container_name: cab-driving-kafka
  depends_on:
    - zookeeper
  ports:
    - "9093:9092"
```

Kafka exposes two advertised listeners:

``` text
PLAINTEXT://cab-driving-kafka:29092
PLAINTEXT_HOST://localhost:9093
```

This distinction matters:

| Client                                        | Kafka Address             |
|-----------------------------------------------|---------------------------|
| Spring Boot app running on host               | `localhost:9093`          |
| Container communicating inside Docker network | `cab-driving-kafka:29092` |

The current local Spring Boot configuration therefore uses:

``` properties
spring.kafka.bootstrap-servers=localhost:9093
```

Kafka is configured with:

``` yaml
KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
```

This is appropriate for the current **single-broker local development
setup**, not a production Kafka cluster.

------------------------------------------------------------------------

# 4. Microservice Responsibilities

| Service            | Responsibilities                                                                            | Storage        |
|--------------------|---------------------------------------------------------------------------------------------|----------------|
| `auth-service`     | User registration/login, RSA JWT issuance, RBAC role assignment, service token generation   | MySQL (auth_db)|
| `location-service` | Receive driver locations (JWT-protected), update Geo index, find nearby drivers, atomic claim (Service-JWT-protected) | Redis |
| `matching-service` | Consume ride requests, find candidate drivers, score & claim driver via Service JWT, publish | MySQL + Feign  |
| `ride-service`     | Create rides, enforce JWT ownership, calculate fare, manage ride lifecycle state machine     | MySQL (uberapp)|

### Auth Service

Owns **identity and trust**.

- Issues RSA-signed JWTs (`sub = userId`, `role = RIDER/DRIVER/ADMIN`) to human users.
- Issues RSA-signed Service JWTs (`sub = matching-service`, `role = SERVICE`) for inter-service communication.
- All other services validate token signatures using the RSA Public Key.

### Location Service

Owns **driver location data**.

It should not own ride state. The driver claim endpoint (`POST /api/v1/drivers/{id}/claim`) is protected with `@PreAuthorize("hasRole('SERVICE')")` — only Matching Service can call it.

### Matching Service

Owns the **matching decision**.

It does not directly modify the ride database. It carries a pre-configured Service JWT that it injects via Feign interceptor for all calls to Location Service.

### Ride Service

Owns **ride state and persistence**.

It reacts to the matching result through Kafka. It enforces resource ownership checks via `SecurityContextHolder` — riders and drivers can only access rides they are participants in. Async Kafka consumer paths (`updateRideWithDriver`) are exempt from human JWT checks.

This separation prevents one service from becoming responsible for the entire business workflow.

------------------------------------------------------------------------

# 5. Communication Strategy

The system intentionally uses both **asynchronous** and **synchronous**
communication.

## 5.1 Kafka — Asynchronous

``` text
ride-service
     │
     │ ride.requested
     ▼
   Kafka
     │
     ▼
matching-service
```

Used when the sender does not need the receiver to complete its work
before continuing.

Benefits:

- Loose coupling
- Independent service scaling
- Buffering during temporary load spikes
- Event-driven workflow

------------------------------------------------------------------------

## 5.2 Feign / REST — Synchronous

``` text
matching-service
       │
       │ HTTP / Feign
       ▼
location-service
       │
       ▼
     Redis
```

Matching requires an immediate list of nearby drivers, so synchronous
communication is appropriate here.

Example:

``` text
GET /api/v1/locations/drivers/nearby
```

Feign is only the HTTP client mechanism.

**Feign does not perform driver matching.**

The matching service receives candidate drivers and performs the
selection logic itself.

------------------------------------------------------------------------

## 5.3 Service-to-Service JWT Security

When Matching Service calls Location Service to claim a driver, the request must be authenticated. Location Service cannot accept arbitrary HTTP clients calling `POST /api/v1/drivers/{id}/claim`.

``` text
Auth Service
     │
     │ POST /auth/service-token
     │ Header: X-Service-Secret: <configured-secret>
     ▼
RSA-signed JWT
     { sub: "matching-service", role: "SERVICE" }
     │
     ▼
Matching Service config
     location.service.token=<service-jwt>
     │
     ▼
Feign Interceptor
     Authorization: Bearer <service-jwt>
     │
     ▼
Location Service
     Spring Security validates JWT signature
     JwtAuthenticationConverter maps role=SERVICE → ROLE_SERVICE
     @PreAuthorize("hasRole('SERVICE')") ✅
```

**Key design decision**: The service JWT is intentionally stored in configuration rather than dynamically fetched and cached at runtime. This keeps the architecture simple for the current project scope — a token-refresh/cache layer would be premature optimization.

------------------------------------------------------------------------

# 6. Auth Service

## Responsibility

The Auth Service is the **single source of trust** in the system. No other service stores passwords or signs tokens.

``` text
Client
  │
  │ POST /auth/register  { name, email, password, role }
  ▼
Auth Service
  │
  ├──► MySQL (auth_db) — save user with BCrypt-hashed password
  │
  │ POST /auth/login  { email, password }
  ▼
Auth Service
  │
  ├──► Validate credentials
  └──► Sign JWT with RSA Private Key
         { sub: userId, role: RIDER/DRIVER/ADMIN }
         → Return { accessToken: "..." }
```

## JWT Structure

``` text
Header:  { alg: RS256 }
Payload: { sub: "20", role: "RIDER", exp: ... }
Signature: signed with RSA Private Key
```

Other services validate the signature using the shared RSA **Public Key** — they never share the private key.

## Service Token Flow

``` text
POST /auth/service-token
Header: X-Service-Secret: <matching-service-secret>
  │
  ▼
Auth Service verifies secret against application config
  │
  └──► Issues JWT: { sub: "matching-service", role: "SERVICE" }
```

## RBAC Roles

| Role      | Assigned to           | Permissions |
|-----------|-----------------------|-------------|
| `RIDER`   | End users requesting rides | `POST /rides/request`, `GET` own rides, `PUT` cancel |
| `DRIVER`  | Drivers in the field  | `POST /locations/update`, driver state transitions |
| `ADMIN`   | Platform operators    | Elevated access |
| `SERVICE` | Internal microservices | `POST /drivers/{id}/claim` (Location Service only) |

------------------------------------------------------------------------

# 7. Location Service

## Responsibility

The Location Service manages frequently changing driver coordinates.

Typical flow:

``` text
Driver
  │
  │ latitude + longitude
  ▼
Location Service
  │
  ▼
Redis Geo Index
```

## Redis Data Structure

Redis stores geospatial locations and atomic claims:

``` text
drivers:location
       └── driverId → longitude + latitude

driver:claim:{driverId} → rideId (TTL: 30s)
ride:claim:{rideId}     → driverId (TTL: 30s)
```

## Atomic Driver Claiming & 30s TTL

To prevent race conditions where multiple concurrent ride requests claim the same driver, `location-service` executes an atomic Redis Lua script (`CLAIM_SCRIPT`):
1. **Key Non-Existence Check**: Checks if `driver:claim:{driverId}` or `ride:claim:{rideId}` exists.
2. **Atomic Reservation with TTL**: If unclaimed, atomically sets both keys with a 30-second TTL (`EX 30`).
3. **Auto Cleanup**: If a claim is abandoned, Redis automatically expires keys after 30 seconds.
4. **Stale Release Protection**: An optional `DELETE /api/v1/drivers/{driverId}/claim?rideId={rideId}` endpoint executes a Lua script (`RELEASE_SCRIPT`) checking `rideId` match before deleting, protecting active claims from being accidentally released by stale processes.

## Nearby Driver Search

The service accepts:

``` text
latitude
longitude
radius
```

and returns nearby drivers with information such as:

``` text
driverId
latitude
longitude
distanceInKm
```

The matching service then consumes this result.

------------------------------------------------------------------------

# 8. Matching Service

## Responsibility

Matching Service performs:

1.  Consume `ride.requested`
2.  Query nearby drivers
3.  Score candidate drivers
4.  Select the best candidate
5.  Publish `ride.matched`

``` text
Kafka
  │
  │ ride.requested
  ▼
Matching Service
  │
  │ Feign
  ▼
Location Service
  │
  ▼
Redis
  │
  │ candidates
  ▼
Matching Service
  │
  │ score
  ▼
Best Driver
  │
  │ ride.matched
  ▼
Kafka
```

## Feign Client

The matching service communicates with Location Service through Spring
Cloud OpenFeign.

Conceptually:

``` java
@FeignClient(
    name = "location-service",
    url = "${location.service.url}"
)
```

Current local configuration:

``` properties
location.service.url=http://localhost:8082
```

------------------------------------------------------------------------

# 9. Ride Service

## Responsibility

Ride Service owns the ride lifecycle **and enforces resource ownership**.

It handles:

- Ride creation
- Ride persistence
- Fare estimation
- Driver assignment
- Ride state transitions
- Ride cancellation
- Ride start
- Ride completion
- **JWT ownership validation** (riders/drivers can only access their own rides)

Database:

``` text
MySQL
   │
   └── rides (uberapp)
```

## Resource Ownership Model

``` text
JWT sub = 20
       │
       ▼
getCurrentUserId() → "20"
       │
       ├── checkOwnership(ride.getRiderId())    // for rider endpoints
       ├── checkOwnership(ride.getDriverId())   // for driver state transitions
       └── checkRideAccess(ride)               // for shared endpoints (cancel)
               ├── currentUserId == ride.riderId  → ALLOW
               └── currentUserId == ride.driverId → ALLOW
               else → 403 Forbidden
```

> **Kafka exemption**: The async `updateRideWithDriver()` consumer path does not run through `SecurityContextHolder` — there is no human JWT in an event-driven invocation.

## Ride Entity

The ride contains information such as:

``` text
id          (UUID, auto-generated)
riderId     (maps to auth-service user.id)
driverId    (populated after matching)
pickup coordinates
drop coordinates
pickup address
drop address
status
estimatedFare
actualFare
createdAt
updatedAt
startedAt
completedAt
```

------------------------------------------------------------------------

# 10. Kafka Event Contracts

## 9.1 `ride.requested`

**Producer:** `ride-service`

**Consumer:** `matching-service`

**Key:** `rideId`

Example payload:

``` json
{
  "rideId": "550e8400-e29b-41d4-a716-446655440000",
  "riderId": "rider-101",
  "pickupLatitude": 12.9716,
  "pickupLongitude": 77.5946,
  "pickupAddress": "MG Road, Bangalore",
  "dropLatitude": 12.9352,
  "dropLongitude": 77.6245,
  "dropAddress": "Koramangala, Bangalore"
}
```

------------------------------------------------------------------------

## 9.2 `ride.matched`

**Producer:** `matching-service`

**Consumer:** `ride-service`

**Key:** `rideId`

Example payload:

``` json
{
  "rideId": "550e8400-e29b-41d4-a716-446655440000",
  "riderId": "rider-101",
  "driverId": "driver-999",
  "driverLatitude": 12.9720,
  "driverLongitude": 77.5950,
  "distanceInKm": 0.45
}
```

### Event Flow

``` text
ride.requested
    │
    ▼
matching-service
    │
    ▼
ride.matched
    │
    ▼
ride-service
```

------------------------------------------------------------------------

# 11. Ride Lifecycle

The ride follows a strict state machine validated by `RideStateTransitionValidator.java`.

``` mermaid
stateDiagram-v2

    [*] --> REQUESTED

    REQUESTED --> MATCHING
    MATCHING --> ACCEPTED
    ACCEPTED --> DRIVER_ARRIVING
    DRIVER_ARRIVING --> RIDE_STARTED
    RIDE_STARTED --> COMPLETED

    REQUESTED --> CANCELLED
    MATCHING --> CANCELLED
    ACCEPTED --> CANCELLED
    DRIVER_ARRIVING --> CANCELLED
    RIDE_STARTED --> CANCELLED
```

| Current State      | Target State       | Allowed | Notes / Triggers |
|--------------------|--------------------|:-------:|------------------|
| `REQUESTED`        | `MATCHING`         |   ✅    | Initial booking (`POST /api/v1/rides/request`). |
| `MATCHING`         | `ACCEPTED`         |   ✅    | Kafka `ride.matched` consumer driver assignment. |
| `ACCEPTED`         | `DRIVER_ARRIVING`  |   ✅    | Driver en route to pickup (`PUT /{id}/arriving`). |
| `DRIVER_ARRIVING`  | `RIDE_STARTED`     |   ✅    | Rider onboarded (`PUT /{id}/start`). |
| `RIDE_STARTED`     | `COMPLETED`        |   ✅    | Trip complete (`PUT /{id}/complete`). |
| `REQUESTED`        | `CANCELLED`        |   ✅    | Cancellation before matching. |
| `MATCHING`         | `CANCELLED`        |   ✅    | Cancellation during matching. |
| `ACCEPTED`         | `CANCELLED`        |   ✅    | Cancellation after driver acceptance. |
| `DRIVER_ARRIVING`  | `CANCELLED`        |   ✅    | Cancellation while driver is arriving. |
| `RIDE_STARTED`     | `CANCELLED`        |   ✅    | Cancellation during ride. |
| `COMPLETED`        | *Any State*        |   ❌    | Terminal state; transition rejected with `IllegalStateException`. |
| `CANCELLED`        | *Any State*        |   ❌    | Terminal state; transition rejected with `IllegalStateException`. |
| `REQUESTED`        | `COMPLETED`        |   ❌    | Direct skip rejected with `IllegalStateException`. |

The application enforces these rules strictly via `RideStateTransitionValidator`, throwing an `IllegalStateException` for any unauthorized or terminal state transition.

------------------------------------------------------------------------

# 12. Core Algorithms

## 11.1 Driver Proximity

Redis Geo is used to efficiently locate drivers within a specified
radius.

Conceptually:

``` text
Pickup Location
      │
      ▼
 Redis Geo Search
      │
      ▼
Nearby Drivers
```

The service can request:

``` text
radius = 5 km
limit = 10
sort = ascending distance
```

------------------------------------------------------------------------

## 11.2 Driver Scoring

The prototype uses a weighted score:

``` text
Distance Weight = 70%
Rating Weight   = 30%
```

Distance score:

``` text
DistanceScore = 1 / (distanceInKm + 0.1)
```

Total score:

``` text
TotalScore =
    (DistanceScore × 0.7)
  + (DriverRating × 0.3)
```

The highest-scoring driver is selected.

> **Prototype note:** The current implementation simulates driver
> ratings. A production system would obtain ratings from a persistent
> driver/profile domain rather than generating them randomly.

------------------------------------------------------------------------

## 11.3 Haversine Distance

Ride Service calculates straight-line distance between pickup and drop
coordinates using the Haversine formula.

``` text
a = sin²(Δφ / 2)
  + cos(φ1) × cos(φ2) × sin²(Δλ / 2)

c = 2 × asin(√a)

distance = R × c
```

where:

``` text
R = 6371 km
```

------------------------------------------------------------------------

## 11.4 Fare Calculation

Current pricing model:

``` text
Base Fare     = ₹50
Distance Rate = ₹12 / km
```

Therefore:

``` text
Estimated Fare =
    50 + (distanceInKm × 12)
```

The calculated value is rounded to two decimal places.

> This is a simplified prototype pricing model, not production surge
> pricing.

------------------------------------------------------------------------

# 13. API Directory

## Auth Service — `localhost:8085`

| Method | Endpoint               | Auth Required          | Purpose                                        |
|--------|------------------------|------------------------|------------------------------------------------|
| `POST` | `/auth/register`       | None                   | Register user with `name`, `email`, `password`, `role` |
| `POST` | `/auth/login`          | None                   | Login, returns `{ accessToken }` JWT           |
| `POST` | `/auth/service-token`  | `X-Service-Secret` header | Generate Service JWT for inter-service auth |

## Location Service — `localhost:8082`

| Method   | Endpoint                                   | Auth Required          | Purpose                     |
|----------|--------------------------------------------|------------------------|---------------------------------|
| `POST`   | `/api/v1/locations/drivers/update`         | `ROLE_DRIVER` JWT      | Driver location update      |
| `GET`    | `/api/v1/locations/drivers/nearby`         | Open (permit all)      | Find nearby drivers         |
| `DELETE` | `/api/v1/locations/drivers/{driverId}`     | `ROLE_DRIVER` JWT      | Remove driver location      |
| `POST`   | `/api/v1/drivers/{driverId}/claim`         | `ROLE_SERVICE` JWT     | Atomic driver claim (30s)   |
| `DELETE` | `/api/v1/drivers/{driverId}/claim`         | `ROLE_SERVICE` JWT     | Release claim (stale safe)  |

## Ride Service — `localhost:8083`

| Method | Endpoint                        | Auth Required            | Ownership Check                    | Purpose                           |
|--------|---------------------------------|--------------------------|------------------------------------|-----------------------------------|
| `POST` | `/api/v1/rides/request`         | `ROLE_RIDER` JWT         | `sub == request.riderId`           | Request a ride                    |
| `GET`  | `/api/v1/rides/{id}`            | JWT (any)                | `sub == riderId OR driverId`       | Get ride details                  |
| `GET`  | `/api/v1/rides/rider/{riderId}` | `ROLE_RIDER` JWT         | `sub == riderId`                   | Get rider rides                   |
| `PUT`  | `/api/v1/rides/{id}/arriving`   | `ROLE_DRIVER` JWT        | `sub == ride.driverId`             | Driver arriving at pickup         |
| `PUT`  | `/api/v1/rides/{id}/start`      | `ROLE_DRIVER` JWT        | `sub == ride.driverId`             | Start ride                        |
| `PUT`  | `/api/v1/rides/{id}/complete`   | `ROLE_DRIVER` JWT        | `sub == ride.driverId`             | Complete ride                     |
| `PUT`  | `/api/v1/rides/{id}/cancel`     | JWT (any)                | `sub == riderId OR driverId`       | Cancel ride                       |

------------------------------------------------------------------------

# 14. End-to-End Flow

``` mermaid
sequenceDiagram

    actor Driver
    actor Rider

    participant Auth as Auth Service
    participant Location as Location Service
    participant Redis
    participant Ride as Ride Service
    participant DB as MySQL
    participant Kafka
    participant Match as Matching Service

    Rider->>Auth: POST /auth/login
    Auth-->>Rider: { accessToken: RIDER_JWT }

    Driver->>Auth: POST /auth/login
    Auth-->>Driver: { accessToken: DRIVER_JWT }

    Driver->>Location: POST /locations/update + DRIVER_JWT
    Location->>Redis: Update driver Geo location

    Rider->>Ride: POST /rides/request + RIDER_JWT
    Ride->>Ride: Validate JWT ownership (sub == riderId)
    Ride->>Ride: Calculate estimated fare
    Ride->>DB: Save REQUESTED ride
    Ride->>Kafka: Publish ride.requested

    Kafka->>Match: Consume ride.requested

    Match->>Location: GET /locations/drivers/nearby (open)
    Location->>Redis: Geo search
    Redis-->>Location: Nearby drivers
    Location-->>Match: Candidate drivers

    Match->>Match: Score candidates
    Match->>Location: POST /drivers/{id}/claim + SERVICE_JWT
    Location->>Redis: Atomic Lua claim script
    Redis-->>Location: Claimed
    Location-->>Match: { claimed: true }

    Match->>Kafka: Publish ride.matched

    Kafka->>Ride: Consume ride.matched (no user JWT)
    Ride->>DB: Assign driver + ACCEPTED

    Driver->>Ride: PUT /rides/{id}/arriving + DRIVER_JWT
    Ride->>Ride: checkOwnership(ride.driverId)
    Ride->>DB: DRIVER_ARRIVING

    Driver->>Ride: PUT /rides/{id}/start + DRIVER_JWT
    Ride->>Ride: checkOwnership(ride.driverId)
    Ride->>DB: RIDE_STARTED

    Driver->>Ride: PUT /rides/{id}/complete + DRIVER_JWT
    Ride->>Ride: checkOwnership(ride.driverId)
    Ride->>DB: COMPLETED + actual fare
```

### Simplified Flow

``` text
0. Rider & Driver authenticate → Auth Service → JWT
        ↓
1. Driver updates location (DRIVER_JWT)
        ↓
2. Location Service → Redis Geo index
        ↓
3. Rider requests ride (RIDER_JWT, ownership validated server-side)
        ↓
4. Ride Service saves ride
        ↓
5. Ride Service → Kafka (ride.requested)
        ↓
6. Matching Service consumes event
        ↓
7. Matching Service → Location Service (GET nearby, open)
        ↓
8. Location Service → Redis Geo search → candidate list
        ↓
9. Matching scores drivers → best candidate selected
        ↓
10. Matching Service → Location Service (POST claim, SERVICE_JWT)
        ↓
11. Location Service → Redis Lua script → atomic claim with 30s TTL
        ↓
12. Matching Service → Kafka (ride.matched)
        ↓
13. Ride Service assigns driver (ACCEPTED) — no user JWT, async consumer
        ↓
14. Driver arriving at pickup (DRIVER_JWT + ownership check)
        ↓
15. Driver starts ride (DRIVER_JWT + ownership check)
        ↓
16. Driver completes ride (DRIVER_JWT + ownership check)
```

------------------------------------------------------------------------

# 15. Failure & Reliability Considerations

The current project is a learning/prototype system, but the architecture
should account for distributed-system failure modes.

## Kafka unavailable

Ride creation and event publishing need a failure strategy.

A production implementation could use:

- Transactional Kafka publishing
- Outbox Pattern
- Retry mechanisms
- Dead-letter topics

## Matching Service unavailable

Kafka retains the `ride.requested` event until a consumer is available.

This allows matching to resume when the service recovers, subject to the
configured Kafka retention and consumer behavior.

## Location Service unavailable

Matching cannot obtain current driver candidates.

The platform implements **Single-Layer Resilience**:
- **Resilient Client (`LocationServiceResilientClient`)**: Wraps Feign REST calls with Resilience4j `@Retry` (3 max attempts) and `@CircuitBreaker` (sliding window 10, minimum calls 5).
- **Clean Service Layer**: `MatchingService` remains clean without duplicate `@Retry` annotations to prevent exponential call multiplication ($3 \times 3 = 9$).
- **Consumer Error Handling**: Spring Kafka `DefaultErrorHandler` handles consumer-level backoff and DLT routing (`ride.requested-dlt`) when downstream services are offline.

## Duplicate Kafka Events & Consumer Idempotency

Kafka consumers are implemented with an **Idempotent Consumer Pattern** to guarantee exact-once business logic processing despite Kafka's at-least-once delivery guarantees:

1. **Persistent Event Store**:
   - `matching-service` persists processed event IDs in table `Requested_processed_events` (`ProcessedEvent` JPA entity).
   - `ride-service` persists processed event IDs in table `Matched_processed_events` (`ProcessedEntity` JPA entity).
2. **`IdempotencyService`**: Encapsulates `isProcessed(eventId)` lookup and `markProcessed(eventId, eventType)` database records.
3. **Consumer Guard Workflow**:
   - Before executing domain logic, `RideEventConsumer` queries `idempotencyService.isProcessed(rideId)`.
   - If `true`, the duplicate event is logged and immediately dropped without executing side effects.
   - If `false`, domain processing runs to completion, and the event ID is stored as processed in MySQL within `idempotencyService.markProcessed(...)`.

## No Drivers Available

Matching should return without publishing a successful match when there
are no eligible drivers.

A production system could then implement:

``` text
Retry matching
      ↓
Expand search radius
      ↓
Notify rider
      ↓
Expire request
```

------------------------------------------------------------------------

# 16. Scalability Considerations

## Ride Service

Can be horizontally scaled because the application layer is designed to
be stateless.

``` text
             ┌── Ride Service #1
Client ──────┼── Ride Service #2
             └── Ride Service #3
                     │
                     ▼
                   MySQL
```

## Matching Service

Kafka consumer groups allow multiple Matching Service instances to
process events in parallel.

``` text
                 Kafka
                   │
        ┌──────────┼──────────┐
        ▼          ▼          ▼
     Match #1   Match #2   Match #3
```

The actual parallelism depends on Kafka topic partitioning.

## Location Service

Multiple Location Service instances can share the same Redis data store.

``` text
Location #1 ─┐
Location #2 ─┼──► Redis
Location #3 ─┘
```

## Redis

Redis is particularly suitable for driver locations because coordinates
change frequently and nearby-driver queries require low latency.

## Kafka

The current Docker setup uses a **single Kafka broker with replication
factor 1**.

For production, Kafka would normally be deployed as a multi-broker
cluster with appropriate:

- Partitions
- Replication factor
- Consumer groups
- Retention policies
- Monitoring
- Failure recovery

------------------------------------------------------------------------

# 🧠 Key Architectural Decisions

| Decision                                          | Reason                                                                    |
|---------------------------------------------------|---------------------------------------------------------------------------|
| Dedicated `auth-service` for JWT issuance         | Single source of trust; no other service signs tokens or stores passwords |
| RSA asymmetric signing (RS256)                    | Services only need the public key to validate — private key stays in auth |
| Service JWT for Matching → Location               | Location Service cannot be callable by arbitrary external clients         |
| Service JWT stored in config (not dynamically fetched) | Avoids premature token-refresh/cache complexity for this project scope |
| Ownership checks via `SecurityContextHolder`      | Server derives identity from validated JWT `sub`, not client request body |
| Kafka consumer exempt from ownership checks       | Async event-driven paths have no human JWT context                        |
| Kafka between Ride and Matching                   | Decouples ride creation from matching                                     |
| Feign between Matching and Location               | Matching needs immediate nearby-driver data                               |
| Redis Geo for locations                           | Fast geospatial lookup for frequently changing data                       |
| MySQL for rides, auth & idempotency               | Durable relational persistence for state & events                         |
| Idempotent Kafka Consumers                        | Prevents duplicate event processing side-effects                          |
| Kafka key = `rideId`                              | Keeps events associated with the same ride                                |
| State machine for rides                           | Prevents invalid lifecycle transitions                                    |

------------------------------------------------------------------------

# 🏁 Architecture Summary

The platform follows a hybrid communication model:

``` text
                 SYNCHRONOUS
        Matching ───────► Location
             │                │
             │                ▼
             │              Redis
             │
             ▼
          ASYNCHRONOUS
        Ride ──► Kafka ──► Matching
                     │
                     └────► Ride
```

The core architectural idea is:

> **Use Kafka for business events that should be decoupled, and
> synchronous REST/Feign calls when a service needs an immediate
> response.**

This gives the project practical exposure to:

- Microservice boundaries
- REST APIs
- OpenFeign
- Kafka producers/consumers
- Redis Geospatial indexing
- MySQL/JPA
- Event-driven architecture
- State machines
- Distributed-system failure modes
- Horizontal scalability
- JWT Authentication (RSA-signed, asymmetric)
- Role-Based Access Control (RBAC)
- Fine-Grained Resource Ownership
- Service-to-Service Security
