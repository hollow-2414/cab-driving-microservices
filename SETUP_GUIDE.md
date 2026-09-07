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
7. [Step 6: End-to-End Testing Walkthrough (cURL Commands)](#step-6-end-to-end-testing-walkthrough-curl-commands)
8. [Step 7: Running Automated Unit Tests](#step-7-running-automated-unit-tests)
9. [Troubleshooting & Common Issues](#troubleshooting--common-issues)

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

`ride-service` requires a MySQL database named `uberapp`.

1. Open your MySQL terminal or database client (e.g., MySQL Workbench, DBeaver, or command line):

```sql
CREATE DATABASE IF NOT EXISTS uberapp;
```

2. Verify or update DB credentials in `ride-service/src/main/resources/application.properties` if needed:
   ```properties
   spring.datasource.url=jdbc:mysql://localhost:3306/uberapp
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

Launch each microservice in a **separate terminal window**:

### Terminal 1: Launch `location-service` (Port 8082)
```bash
cd location-service
mvn spring-boot:run
```
*Health Check*: `http://localhost:8082/actuator/health`

---

### Terminal 2: Launch `ride-service` (Port 8083)
```bash
cd ride-service
mvn spring-boot:run
```
*Health Check*: `http://localhost:8083/actuator/health`

---

### Terminal 3: Launch `matching-service` (Port 8084)
```bash
cd matching-service
mvn spring-boot:run
```
*Health Check*: `http://localhost:8084/actuator/health`

---

## Step 6: End-to-End Testing Walkthrough (cURL Commands)

Follow these copy-pasteable cURL commands to simulate a complete ride lifecycle:

### Step 6.1: Add Active Drivers (Driver Telemetry API)
Update driver location coordinates in Bangalore ($12.9716^\circ\text{N}, 77.5946^\circ\text{E}$):

```bash
curl -X POST http://localhost:8082/api/locations \
  -H "Content-Type: application/json" \
  -d '{
    "driverId": "driver-101",
    "latitude": 12.9720,
    "longitude": 77.5950
  }'
```
*Expected Response*: HTTP 200 OK — `"Driver location updated successfully"`

---

### Step 6.2: Query Nearby Drivers
Check if the driver is indexed in Redis within a $5\text{ km}$ radius:

```bash
curl -X GET "http://localhost:8082/api/locations/nearby?latitude=12.9716&longitude=77.5946&radius=5.0"
```
*Expected Response*: JSON array containing `driver-101` with distance $\sim 0.06\text{ km}$.

---

### Step 6.3: Rider Requests a Cab
Submit a ride request from MG Road to Koramangala:

```bash
curl -X POST http://localhost:8083/api/rides \
  -H "Content-Type: application/json" \
  -d '{
    "riderId": "rider-001",
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
  "id": "RIDE_UUID_HERE",
  "riderId": "rider-001",
  "status": "MATCHING",
  "estimatedFare": 112.76,
  "pickupAddress": "MG Road, Bangalore",
  "dropAddress": "Koramangala, Bangalore"
}
```

---

### Step 6.4: Verify Automatic Driver Assignment (Kafka Event)
Copy the `id` returned from Step 6.3 and check the ride status:

```bash
curl -X GET http://localhost:8083/api/rides/<RIDE_UUID_HERE>
```
*Expected Response*: The status will automatically transition from `MATCHING` to `ACCEPTED`, and `driverId` will be updated to `"driver-101"` via the Kafka `ride.matched` event consumer!

---

### Step 6.5: Driver Starts the Ride
Simulate driver clicking "Start Ride":

```bash
curl -X PUT http://localhost:8083/api/rides/<RIDE_UUID_HERE>/start
```
*Expected Response*: Status updates to `"RIDE_STARTED"`, `startedAt` timestamp is populated.

---

### Step 6.6: Driver Completes the Ride
Simulate trip completion:

```bash
curl -X PUT http://localhost:8083/api/rides/<RIDE_UUID_HERE>/complete
```
*Expected Response*: Status updates to `"COMPLETED"`, `actualFare` is finalized, `completedAt` timestamp is set.

---

## Step 7: Running Automated Unit Tests

To run unit tests across all microservices without launching external infrastructure:

```bash
# Test Location Service
cd location-service
mvn test-compile surefire:test "-Dtest=LocationServiceTest"

# Test Matching Service
cd matching-service
mvn test-compile surefire:test "-Dtest=MatchingServiceTest,RideEventConsumerTest"

# Test Ride Service
cd ride-service
mvn test-compile surefire:test "-Dtest=RideServiceTest,RideEventConsumerTest"
```

---

## Troubleshooting & Common Issues

| Issue | Root Cause | Solution |
| :--- | :--- | :--- |
| **`Connection refused to localhost:6380`** | Redis Docker container is down. | Run `docker compose up -d` and check with `docker ps`. |
| **`Connection refused to localhost:9093`** | Kafka Docker container is starting up or failed. | Wait 15 seconds for Zookeeper & Kafka initialization or run `docker compose logs kafka`. |
| **`Access denied for user 'root'@'localhost'`** | MySQL credentials mismatch. | Update `spring.datasource.password` in `ride-service/src/main/resources/application.properties`. |
| **`Unknown database 'uberapp'`** | MySQL database not created yet. | Execute `CREATE DATABASE uberapp;` in MySQL console. |
| **`Port 8082 / 8083 / 8084 already in use`** | Another instance or process is occupying the port. | Terminate the process using `taskkill /PID <pid> /F` (Windows) or `kill -9 <pid>` (Linux/macOS). |

---

*Setup guide complete! You are ready to run and scale the Cab-Driving Microservices System.*
