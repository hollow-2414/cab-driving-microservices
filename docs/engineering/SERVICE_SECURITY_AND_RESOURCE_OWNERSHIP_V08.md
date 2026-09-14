# 🛡️ Service-to-Service Security & Resource Ownership (v0.8)

> **TL;DR**: Service-to-Service authentication (Matching $\rightarrow$ Location via RSA Service JWTs) and fine-grained Resource Ownership validation to eliminate identity spoofing.

---

## ⚡ 1. Service-to-Service Security (Matching $\rightarrow$ Location)

* **Problem**: Location Service needed to distinguish trusted Matching Service calls from external clients.
* **Service Token Endpoint**: `POST /auth/service-token` with header POST /auth/service-token
  Header: X-Service-Secret: <SERVICE_SECRET>.
* **Payload**: `sub: matching-service`, `role: SERVICE`, signed with Auth Service RSA Private Key.
* **Propagation**: Matching Service passes `Authorization: Bearer <service-jwt>` via OpenFeign interceptor.
* **Validation**: Location Service validates token signature & enforces `@PreAuthorize("hasRole('SERVICE')")` on `POST /api/v1/drivers/{driverId}/claim`.

```
Ride Event ──► Matching Service ──► Claim Driver (Bearer Service JWT) ──► Location Service (200 OK)
```

---

## 🔐 2. Fine-Grained Resource Ownership Enforcement

* **RBAC vs Ownership**: RBAC validates user role (`RIDER`/`DRIVER`); Ownership validates resource association (`sub == ride.riderId / driverId`).
* **Vulnerability Fixed**: Request body containing `{"riderId": "21"}` trusted by server despite JWT `sub = 20`. Now validated against `SecurityContextHolder`.
* **JWT Reusability**: Tokens represent identity (`sub`), not single-use actions.
* **Kafka Exemption**: Async Kafka consumer updates (`updateRideWithDriver`) are internal system events and exempt from human JWT checks.

### Ownership Helpers (`RideService.java`)
```java
private String getCurrentUserId() {
    return SecurityContextHolder.getContext().getAuthentication().getName();
}

private void checkOwnership(String ownerId) {
    if (!getCurrentUserId().equals(ownerId)) throw new AccessDeniedException("Access denied");
}

private void checkRideAccess(Ride ride) {
    String uid = getCurrentUserId();
    if (!uid.equals(ride.getRiderId()) && !uid.equals(ride.getDriverId())) {
        throw new AccessDeniedException("Access denied");
    }
}
```

---

## 🧪 3. Operation Scoping & Test Matrix

| Operation / Endpoint | Scope / Actor | Ownership Rule | Result |
| :--- | :--- | :--- | :--- |
| `POST /api/v1/rides/request` | Rider | `currentUserId == request.riderId` | ✅ PASSED |
| `GET /api/v1/rides/rider/{riderId}` | Rider | `checkOwnership(riderId)` | ✅ PASSED |
| `/arriving`, `/start`, `/complete` | Assigned Driver | `checkOwnership(ride.getDriverId())` | ✅ PASSED |
| `PUT /api/v1/rides/{id}/cancel` | Participant | `checkRideAccess(ride)` | ✅ PASSED |
| Cross-Rider Access (sub 21 $\rightarrow$ ride 20) | Unauthorized | Denied (`403 Forbidden`) | ✅ PASSED |
| Inter-Service Claim | `matching-service` | `hasRole('SERVICE')` | ✅ PASSED |

---

## 🎯 Security Matrix

| Capability | Status |
| :--- | :--- |
| JWT Authentication & Principal Resolution | ✅ PASSED |
| Role-Based Access Control (RBAC) | ✅ PASSED |
| Fine-Grained Resource Ownership (Rider & Driver) | ✅ PASSED |
| Request Body Identity Spoofing Prevention | ✅ PASSED |
| Service-to-Service Security (Matching $\rightarrow$ Location) | ✅ PASSED |
| Asynchronous Outbox / Kafka Flow Integrity | ✅ PASSED |
