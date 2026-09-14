# Cab-Driving Microservices Platform — Quick Start & Setup Guide

This guide provides step-by-step instructions for cloning, setting up infrastructure, building, running, and testing the **Cab-Driving Microservices Platform** on any environment.

---

## 📋 Table of Contents
1. [Prerequisites](#1-prerequisites)
2. [Step 1: Clone the Repository](#step-1-clone-the-repository)
3. [Step 2: Database Setup (MySQL)](#step-2-database-setup-mysql)
4. [Step 3: Start Infrastructure Services (Redis & Kafka via Docker)](#step-3-start-infrastructure-services-redis--kafka-via-docker)
5. [Step 4: Build Microservices](#step-4-build-microservices)
6. [Step 5: Run the Microservices](#step-5-run-the-microservices)
7. [Step 6: Obtain a JWT (Required for all protected endpoints)](#step-6-obtain-a-jwt)
8. [Step 7: End-to-End Testing Walkthrough (cURL Commands)](#step-7-end-to-end-testing-walkthrough-curl-commands)
9. [Step 8: Running Automated Unit Tests](#step-8-running-automated-unit-tests)
10. [Troubleshooting & Common Issues](#troubleshooting--common-issues)

---

## 1. Prerequisites

Before starting, ensure you have the following installed on your machine:

- **JDK 21** (or Java 21 JDK): Verify with `java -version`
- **Apache Maven 3.8+**: Verify with `mvn -v`
- **Docker Desktop** (with Docker Compose): Verify with `docker --version` and `docker compose version`
- **MySQL Database Server**: Verify service is running on port `3306`
- **cURL** or **Postman**: For sending HTTP requests

---

## Step 1: Clone the Repository

Clone the project from GitHub and navigate into the root directory:

```bash
git clone <YOUR_GIT_REPOSITORY_URL>
cd cab-driving
```

---

## Step 2: Database Setup (MySQL)

Two databases are required: `uberapp` (for `ride-service`) and `auth_db` (for `auth-service`).

1. Open your MySQL terminal or database client (e.g., MySQL Workbench, DBeaver, or command line):

```sql
CREATE DATABASE IF NOT EXISTS uberapp;
CREATE DATABASE IF NOT EXISTS auth_db;
```

2. Verify or update DB credentials in each service's `application.properties` if needed:
   ```properties
   # ride-service/src/main/resources/application.properties
   spring.datasource.url=jdbc:mysql://localhost:3306/uberapp
   spring.datasource.username=root
   spring.datasource.password=YOUR_MYSQL_PASSWORD

   # auth-service/src/main/resources/application.properties
   spring.datasource.url=jdbc:mysql://localhost:3306/auth_db
   spring.datasource.username=root
   spring.datasource.password=YOUR_MYSQL_PASSWORD
   ```

---

## Step 3: Start Infrastructure Services (Redis & Kafka via Docker)

The project includes a `docker-compose.yml` file that provisions **Redis** (Port `6380`), **Apache Kafka** (Port `9093`), and **Zookeeper** (Port `2182`).

Run the following command from the project root directory:

```bash
docker compose up -d
```

### Verify Running Containers:
```bash
docker ps
```

You should see 3 active containers running:
- `cab-driving-redis` (Port `6380:6379`)
- `cab-driving-zookeeper` (Port `2182:2181`)
- `cab-driving-kafka` (Port `9093:9092`)

---

## Step 4: Build Microservices

Build and package each microservice JAR using Maven:

### Option A: Build All Services sequentially
```bash
# Build auth-service
cd auth-service
mvn clean package -DskipTests
cd ..

# Build location-service
cd location-service
mvn clean package -DskipTests
cd ..

# Build matching-service
cd matching-service
mvn clean package -DskipTests
cd ..

# Build ride-service
cd ride-service
mvn clean package -DskipTests
cd ..
```

---

## Step 5: Run the Microservices

Launch each microservice in a **separate terminal window**. Start `auth-service` first — other services validate JWTs using its RSA public key.

### Terminal 1: Launch `auth-service` (Port 8085)
```bash
cd auth-service
mvn spring-boot:run
```

---

### Terminal 2: Launch `location-service` (Port 8082)
```bash
cd location-service
mvn spring-boot:run
```
*Health Check*: `http://localhost:8082/actuator/health`

---

### Terminal 3: Launch `ride-service` (Port 8083)
```bash
cd ride-service
mvn spring-boot:run
```
*Health Check*: `http://localhost:8083/actuator/health`

---

### Terminal 4: Launch `matching-service` (Port 8084)
```bash
cd matching-service
mvn spring-boot:run
```
*Health Check*: `http://localhost:8084/actuator/health`

---

## Step 6: Obtain a JWT

All protected endpoints require a valid JWT. Register and login first:

### Step 6.1: Register a Rider
```bash
curl -X POST http://localhost:8085/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name": "Alice", "email": "alice@example.com", "password": "password123", "role": "RIDER"}'
```
*Expected Response*: `"User registered successfully with id: <USER_ID>"`

---

### Step 6.2: Register a Driver
```bash
curl -X POST http://localhost:8085/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name": "Bob", "email": "bob@example.com", "password": "password123", "role": "DRIVER"}'
```

---

### Step 6.3: Login and Save JWT
```bash
# Login as Rider
curl -X POST http://localhost:8085/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "alice@example.com", "password": "password123"}'
# Response: {"accessToken": "<RIDER_JWT>"}

# Login as Driver
curl -X POST http://localhost:8085/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "bob@example.com", "password": "password123"}'
# Response: {"accessToken": "<DRIVER_JWT>"}
```
> Copy and save both tokens. Replace `<RIDER_JWT>` and `<DRIVER_JWT>` in the steps below.

---

## Step 7: End-to-End Testing Walkthrough (cURL Commands)

### Step 7.1: Register Driver Location
Update driver location coordinates in Bangalore ($12.9716^\circ\text{N}, 77.5946^\circ\text{E}$):

```bash
curl -X POST http://localhost:8082/api/v1/locations/drivers/update \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <DRIVER_JWT>" \
  -d '{"driverId": "<DRIVER_USER_ID>", "latitude": 12.9720, "longitude": 77.5950}'
```
*Expected Response*: HTTP 200 OK — `"Driver location updating"`

---

### Step 7.2: Query Nearby Drivers (Open Endpoint)
```bash
curl -X GET "http://localhost:8082/api/v1/locations/drivers/nearby?latitude=12.9716&longitude=77.5946&radius=5.0"
```
*Expected Response*: JSON array with nearby driver within $\sim 0.06\text{ km}$.

---

### Step 7.3: Rider Requests a Cab
Submit a ride request from MG Road to Koramangala:

```bash
curl -X POST http://localhost:8083/api/v1/rides/request \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <RIDER_JWT>" \
  -d '{
    "riderId": "<RIDER_USER_ID>",
    "pickupLatitude": 12.9716,
    "pickupLongitude": 77.5946,
    "pickupAddress": "MG Road, Bangalore",
    "dropLatitude": 12.9352,
    "dropLongitude": 77.6245,
    "dropAddress": "Koramangala, Bangalore"
  }'
```
*Expected Response*:
```json
{
  "id": "<RIDE_UUID>",
  "riderId": "<RIDER_USER_ID>",
  "status": "MATCHING",
  "estimatedFare": 112.76,
  "pickupAddress": "MG Road, Bangalore",
  "dropAddress": "Koramangala, Bangalore"
}
```

---

### Step 7.4: Verify Automatic Driver Assignment (Kafka Event)
Copy the `id` from the previous step and poll for driver assignment:

```bash
curl -X GET http://localhost:8083/api/v1/rides/<RIDE_UUID> \
  -H "Authorization: Bearer <RIDER_JWT>"
```
*Expected*: Status transitions from `MATCHING` → `ACCEPTED`, `driverId` is populated via `ride.matched` Kafka event.

---

### Step 7.5: Driver Arrives
```bash
curl -X PUT http://localhost:8083/api/v1/rides/<RIDE_UUID>/arriving \
  -H "Authorization: Bearer <DRIVER_JWT>"
```
*Expected*: Status → `DRIVER_ARRIVING`.

---

### Step 7.6: Driver Starts the Ride
```bash
curl -X PUT http://localhost:8083/api/v1/rides/<RIDE_UUID>/start \
  -H "Authorization: Bearer <DRIVER_JWT>"
```
*Expected*: Status → `RIDE_STARTED`, `startedAt` populated.

---

### Step 7.7: Driver Completes the Ride
```bash
curl -X PUT http://localhost:8083/api/v1/rides/<RIDE_UUID>/complete \
  -H "Authorization: Bearer <DRIVER_JWT>"
```
*Expected*: Status → `COMPLETED`, `actualFare` finalized, `completedAt` set.

---

## Step 8: Running Automated Unit Tests

To run unit tests across all microservices without launching external infrastructure:

```bash
# Auth Service Unit Tests
cd auth-service && mvn test-compile surefire:test

# Location Service Unit Tests
cd location-service && mvn test-compile surefire:test "-Dtest=LocationServiceTest,DriverClaimServiceTest"

# Matching Service Unit Tests
cd matching-service && mvn test-compile surefire:test "-Dtest=MatchingServiceTest,RideEventConsumerTest"

# Ride Service Unit Tests
cd ride-service && mvn test-compile surefire:test "-Dtest=RideServiceTest,RideEventConsumerTest"
```

---

## Troubleshooting & Common Issues

| Issue | Root Cause | Solution |
| :--- | :--- | :--- |
| **`Connection refused to localhost:6380`** | Redis Docker container is down. | Run `docker compose up -d` and check with `docker ps`. |
| **`Connection refused to localhost:9093`** | Kafka Docker container is starting up or failed. | Wait 15 seconds for Zookeeper & Kafka initialization or run `docker compose logs kafka`. |
| **`Access denied for user 'root'@'localhost'`** | MySQL credentials mismatch. | Update `spring.datasource.password` in each service's `application.properties`. |
| **`Unknown database 'uberapp'`** | MySQL database not created yet. | Execute `CREATE DATABASE uberapp;` in MySQL console. |
| **`Unknown database 'auth_db'`** | Auth DB not created yet. | Execute `CREATE DATABASE auth_db;` in MySQL console. |
| **`Port 8082 / 8083 / 8084 / 8085 already in use`** | Another instance or process is occupying the port. | Terminate the process using `taskkill /PID <pid> /F` (Windows) or `kill -9 <pid>` (Linux/macOS). |
| **`401 Unauthorized` on any endpoint** | Missing or expired JWT in `Authorization` header. | Re-login via `POST /auth/login` and add `Authorization: Bearer <token>` to your request. |
| **`403 Forbidden` on a ride endpoint** | JWT sub does not match the ride's `riderId` or `driverId`. | Ensure you are using the correct JWT for the user associated with this ride. |
| **`403 Forbidden` on driver claim**  | Matching Service is not sending a valid Service JWT. | Check `location.service.token` config in `matching-service/application.properties`. |

---

*Setup guide complete! You are ready to run and scale the Cab-Driving Microservices System.*
