# 🚗 Atomic Driver Claiming & Stale Release Protection

> **TL;DR**: Prevents race conditions and double-booking when claiming drivers using atomic Redis Lua scripts with a 30-second TTL and stale release validation.

---

## ⚡ How It Works

1. **Atomic Claim**: `POST /api/v1/drivers/{driverId}/claim` runs a Redis Lua script checking key non-existence. If free, sets claim keys with 30s TTL.
2. **Auto Cleanup**: If a claim is abandoned, Redis automatically expires the reservation after 30 seconds.
3. **Stale Release Protection**: `DELETE /api/v1/drivers/{driverId}/claim?rideId={rideId}` verifies that the caller's `rideId` matches the active Redis key before deleting. If a newer ride has claimed the driver, release is safely rejected (`claimed: false`).

```
Claim Request ──► Redis Lua (EXISTS?) ──┬── NO  ──► Set Driver+Ride Keys (30s TTL) ──► Claimed: true
                                        └── YES ──► Reject Concurrent Claim    ──► Claimed: false
```

---

## 💾 Redis Keys

- **`driver:claim:{driverId}`** $\rightarrow$ Value: `rideId` (TTL: 30s)
- **`ride:claim:{rideId}`** $\rightarrow$ Value: `driverId` (TTL: 30s)

---

## 🌐 API Endpoints

- **POST** `/api/v1/drivers/{driverId}/claim` `{"rideId": "R400"}` $\rightarrow$ `{"claimed": true}`
- **DELETE** `/api/v1/drivers/{driverId}/claim?rideId=R300` $\rightarrow$ `{"claimed": false}` *(if R300 is stale)*

---

## 📁 Key Files

- 🛠️ **Controller**: [`DriverClaimController.java`](../../location-service/src/main/java/com/rideshare/locationservice/controller/DriverClaimController.java)
- 🛠️ **Service**: [`DriverClaimService.java`](../../location-service/src/main/java/com/rideshare/locationservice/service/DriverClaimService.java)
- 🧪 **Tests**: [`DriverClaimServiceTest.java`](../../location-service/src/test/java/com/rideshare/locationservice/service/DriverClaimServiceTest.java)
