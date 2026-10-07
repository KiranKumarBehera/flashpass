# ⚡ FlashPass — Distributed High-Concurrency Stadium Ticketing Engine

[![Live Demo](https://img.shields.io/badge/Live%20Demo-flashpass--three.vercel.app-00df98?style=for-the-badge&logo=vercel&logoColor=white)](https://flashpass-three.vercel.app)
[![Backend Engine](https://img.shields.io/badge/Backend%20Engine-Render%20Cloud-46E3B7?style=for-the-badge&logo=render&logoColor=white)](https://flashpass-engine.onrender.com/api/events)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3%20%2F%20Java%2017-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io)
[![React](https://img.shields.io/badge/React%2019-Vite%20SPA-61DAFB?style=for-the-badge&logo=react&logoColor=black)](https://react.dev)
[![PostgreSQL](https://img.shields.io/badge/Neon-Cloud%20PostgreSQL%2016-336791?style=for-the-badge&logo=postgresql&logoColor=white)](https://neon.tech)
[![Redis](https://img.shields.io/badge/Upstash-Cloud%20Redis%20TLS-DC382D?style=for-the-badge&logo=redis&logoColor=white)](https://upstash.com)
[![WebSockets](https://img.shields.io/badge/STOMP-WebSockets%20PubSub-010101?style=for-the-badge&logo=socketdotio&logoColor=white)](https://stomp.github.io)
[![Docker](https://img.shields.io/badge/Docker-Multi--Stage%20Alpine-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://docker.com)

> **FlashPass** is an enterprise-grade, high-throughput distributed concert and stadium reservation engine engineered to eliminate race conditions, double-booking, and seat hoarding during flash-sale traffic stampedes (e.g., Coldplay, Taylor Swift, Diljit Dosanjh tour drops).
> Built with **Spring Boot 3.3**, **React 19**, **Neon Serverless PostgreSQL**, **Upstash Cloud Redis (TLS)**, and **STOMP WebSockets**, containerized via **Multi-Stage Docker**, and deployed on a **100% Free Forever** cloud architecture.

---

## 🌐 Live Cloud Deployment

- **Production Frontend:** [https://flashpass-three.vercel.app](https://flashpass-three.vercel.app) (Hosted on Vercel Anycast Edge CDN)
- **Production Backend Engine:** [https://flashpass-engine.onrender.com](https://flashpass-engine.onrender.com/api/events) (Containerized on Render Cloud)
- **Source Repository:** [https://github.com/KiranKumarBehera/flashpass](https://github.com/KiranKumarBehera/flashpass)

### 🔑 Demo Credentials (Role-Based Access Control)
| Role | Username | Password | Privileges |
|---|---|---|---|
| **Fan** | `kiran` | `pass123` | Browse shows, acquire 5-min seat holds, purchase tickets, view holographic passes, cancel bookings. |
| **Organizer / Admin** | `organizer` | `admin123` | All fan privileges + Register stadium venues, schedule concert dates with dynamic seat matrices, view real-time revenue telemetry, reset stadium inventory. |

---

## 🏗️ System Architecture

```
                                      [ CLIENT LAYER ]
                     Web & Mobile Viewports (Vercel Anycast Edge CDN)
                                             │
                       ┌─────────────────────┴─────────────────────┐
                       │ HTTPS REST (JSON API)                     │ WSS Native WebSocket (STOMP)
                       ▼                                           ▼
       ┌───────────────────────────────────────────────────────────────────────────┐
       │                       FLASHPASS ENGINE (Render Cloud)                     │
       │                        Spring Boot 3.3 / Java 17 LTS                      │
       │                                                                           │
       │   ┌───────────────────────────┐           ┌───────────────────────────┐   │
       │   │  Global Exception Advice  │           │    STOMP Message Broker   │   │
       │   │  @RestControllerAdvice    │           │  PubSub Broadcast Engine  │   │
       │   │  (OptimisticLock -> 409)  │           │   /topic/* (<30ms Sync)   │   │
       │   └─────────────┬─────────────┘           └─────────────▲─────────────┘   │
       │                 │                                       │                 │
       │   ┌─────────────▼─────────────┐                         │                 │
       │   │      SeatService          ├─────────────────────────┘                 │
       │   │   Atomic Lock / Release   │ Topics: /topic/seats, /topic/venues,      │
       │   │   @Transactional Lease    │         /topic/events, /topic/analytics   │
       │   └─────────────┬─────────────┘                                           │
       │                 │                                                         │
       │   ┌─────────────▼─────────────┐           ┌───────────────────────────┐   │
       │   │ SeatLockExpirationWorker  │           │   DynamicPricingEngine    │   │
       │   │ Scheduled TTL Sweeper     │           │   Exponential Occupancy   │   │
       │   └───────────────────────────┘           └───────────────────────────┘   │
       └─────────────────┬───────────────────────────────────────┬─────────────────┘
                         │                                       │
              Cache-Aside Pattern (TTL 10m)                      │ ACID Transactions
              Automatic Cluster Invalidation                     │ Atomic @Version Increment
                         ▼                                       ▼
             ┌───────────────────────┐               ┌───────────────────────┐
             │  Upstash Cloud Redis  │               │ Neon PostgreSQL Cloud │
             │ In-Memory Read Cache  │               │ ACID Relational Store │
             │  Sub-10ms Seat State  │               │ AWS us-east-2 (TLS)   │
             └───────────────────────┘               └───────────────────────┘
```

---

## 🏛️ The Four Core Architectural Pillars

### 1. 🛡️ Concurrency Defense & Optimistic Locking (`@Version`)
- **The Problem:** When 100,000 concurrent fans click the exact same front-row seat simultaneously, standard database reads result in race conditions, overwrites, and catastrophic double-bookings.
- **The Solution:** Hibernate JPA Optimistic Locking via a dedicated `@Version` column.
- **Mechanism:** Every lock transaction issues an atomic conditional update:
  ```sql
  UPDATE seats SET status = 'LOCKED', locked_by = 'kiran', version = 1 
  WHERE id = 42 AND version = 0;
  ```
  The first thread succeeds and increments `version` to `1`. The subsequent 99,999 concurrent threads fail because `version = 0` no longer matches. Hibernate raises `ObjectOptimisticLockingFailureException`, which our global `@RestControllerAdvice` instantly maps to **HTTP 409 Conflict** in sub-30ms without thread starvation or pessimistic database row locks.

### 2. ⚡ Sub-10ms Distributed Caching (Cache-Aside Pattern)
- High-read stadium seat queries are cached in **Upstash Cloud Redis (TLS)**.
- Query latency is reduced from **~400ms (database roundtrip) to <10ms**.
- **Event-Driven Cache Eviction:** Whenever an inventory mutation occurs (seat locked, purchased, cancelled, or reset), `@CacheEvict(value = "eventSeats", key = "#eventId")` instantly purges stale data, ensuring fans always view synchronized inventory.

### 3. 📢 Universal Multi-Topic STOMP WebSockets (<30ms Sync)
- Pure native WebSocket transport (`/ws-flashpass`) using STOMP over TLS.
- Zero client polling overhead; updates broadcast reactively across 5 dedicated topics:
  - `/topic/seats`: Instant seat lock, booking, and release events.
  - `/topic/venues`: Real-time registration of new arena venues.
  - `/topic/events`: Real-time scheduling of tour shows with auto-generated seat layouts.
  - `/topic/analytics`: Live financial telemetry (Gross Revenue, Occupancy Rate) for organizers.
  - `/topic/events/reset`: Instant stadium wipe notification that refreshes all connected viewports simultaneously.

### 4. 🔐 Defense-in-Depth RBAC & Secure Pass Lifecycle
- Domain-driven security separating `ROLE_FAN` from `ROLE_ORGANIZER` and `ROLE_ADMIN`.
- Administrative endpoints (`POST /api/events`, `POST /api/venues`, `POST /api/events/{id}/reset`) enforce database-verified caller identity, returning **HTTP 403 Forbidden** for unauthorized access.
- Ticket holders can cancel their confirmed pass directly from the **Holographic Passbook**. `SeatService.releaseSeat()` verifies identity, releases the seat back to `AVAILABLE`, and triggers a real-time STOMP broadcast.

---

## ✨ Features & User Experience

| Feature | Description |
|---|---|
| **🏟️ Curved Amphitheater Stadium** | Realistic 3D-angled stadium bowl with tiered rows (VIP Floor & Standard Bowl), status color codes, and live pricing. |
| **📱 Flawless Mobile Responsiveness** | Scaled 27px seats with 4px gaps fitting any 360px–390px phone screen without row wrapping; smooth horizontal momentum touch panning (`-webkit-overflow-scrolling: touch`). |
| **🎫 Holographic Passbook** | Cyberpunk holographic digital pass cards with dynamic SVG barcodes, show countdowns, ticket metadata, and cancellation triggers. |
| **⚡ 10-Bot Concurrency Simulator** | Built-in stress test that launches 10 concurrent HTTP threads racing for the same seat, visually verifying Optimistic Locking (1 winner, 9 conflicts). |
| **⏱️ Dual-Layer TTL Expiration** | 5-minute client reservation countdown paired with a background daemon (`@Scheduled(fixedRate = 1000)`) that auto-releases abandoned carts. |
| **📈 Dynamic Pricing Engine** | Real-time price escalation based on stadium occupancy percentages using an exponential demand curve. |
| **📊 Organizer Telemetry Console** | Real-time financial dashboard displaying gross revenue, sold counts, hold counts, and occupancy percentages over WebSockets. |

---

## 📁 Monorepo Structure

```
flashpass/
├── flashpass-engine/                 # Spring Boot 3.3 Backend API & WebSocket Broker
│   ├── src/main/java/com/kiran/flashpassengine/
│   │   ├── config/                   # WebSocketConfig, RedisCacheConfig, SecurityConfig
│   │   ├── controller/               # EventController, VenueController, AuthController
│   │   ├── model/                    # Seat, Event, Venue, User, UserRole, SeatStatus
│   │   ├── repository/               # SeatRepository, EventRepository, VenueRepository
│   │   ├── service/                  # SeatService, AuthService, DynamicPricingEngine
│   │   └── worker/                   # SeatLockExpirationWorker (@Scheduled)
│   ├── src/test/java/                # Concurrency stress tests & Unit test suites
│   ├── Dockerfile                    # Multi-stage Maven Alpine -> JRE 17 Alpine (~150MB)
│   └── pom.xml
│
├── flashpass-ui/                     # React 19 + Vite Frontend Single Page Application
│   ├── src/
│   │   ├── components/               # HolographicPass, TelemetryConsole, SeatModal
│   │   ├── App.jsx                   # STOMP client, RBAC session, stadium state machine
│   │   └── App.css                   # Responsive styles, glassmorphism, mobile touch pan
│   ├── Dockerfile                    # Multi-stage Node 20 -> Nginx Alpine (~25MB)
│   ├── nginx.conf                    # Nginx reverse proxy configuration
│   └── package.json
│
├── docker-compose.yml                # Unified multi-container orchestration
├── .gitignore                        # Clean exclusion rules (ignores build & local files)
└── README.md                         # Production documentation
```

---

## 🚀 Running Locally

### Option 1: Using Docker Compose (Fastest)

Clone the repository and run:
```bash
git clone https://github.com/KiranKumarBehera/flashpass.git
cd flashpass
docker-compose up --build
```
- **Frontend UI:** `http://localhost:80`
- **Backend API:** `http://localhost:8080/api/events`

---

### Option 2: Running Standalone

#### Prerequisites
- **Java 17+ LTS**
- **Maven 3.9+**
- **Node.js 20+ & npm**
- Local or Cloud **PostgreSQL** & **Redis** instances

#### 1. Configure Backend Environment
Set environment variables or update `flashpass-engine/src/main/resources/application.properties`:
```properties
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/flashpass
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379
```

#### 2. Start Backend Engine
```bash
cd flashpass-engine
mvn clean spring-boot:run
```

#### 3. Start Frontend SPA
```bash
cd flashpass-ui
npm install
npm run dev
```
Open `http://localhost:5173` in your browser.

---

## 📡 REST API & WebSocket Topics

### REST Endpoints
| Method | Endpoint | Access | Description |
|---|---|---|---|
| `GET` | `/api/events` | Public | List all listed concert tour dates |
| `GET` | `/api/events/{id}/seats` | Public | Fetch all seats for an event (Redis Cached) |
| `POST` | `/api/seats/{id}/lock?user={u}` | Authenticated | Acquire a 5-minute atomic optimistic lock |
| `POST` | `/api/seats/{id}/book?user={u}` | Authenticated | Finalize purchase and issue confirmed ticket |
| `POST` | `/api/seats/{id}/release?user={u}` | Owner / Admin | Release held seat or cancel and refund booked ticket |
| `POST` | `/api/events?user={u}` | Organizer Only | Schedule new event date & auto-generate seat layout |
| `POST` | `/api/events/{id}/reset?user={u}` | Organizer Only | Reset all seats back to `AVAILABLE` (403 for Fans) |
| `GET` | `/api/venues` | Public | List registered stadium venues |
| `POST` | `/api/venues?user={u}` | Organizer Only | Register a new arena venue |
| `POST` | `/api/auth/login` | Public | Authenticate user and return role profile |
| `POST` | `/api/auth/register` | Public | Register new fan or organizer account |

### STOMP WebSocket Topics (`/ws-flashpass`)
| Topic | Payload | Description |
|---|---|---|
| `/topic/seats` | `Seat` | Broadcasts seat lock, booking, and release mutations |
| `/topic/venues` | `Venue` | Broadcasts newly registered venues across clients |
| `/topic/events` | `Event` | Broadcasts new concert listings across clients |
| `/topic/analytics` | `Map<String, Object>` | Broadcasts real-time gross revenue and occupancy |
| `/topic/events/reset` | `Map<String, Object>` | Broadcasts stadium reset events across all clients |

---

## 🧪 Concurrency Stress Testing Verification

To empirically prove race condition defense, FlashPass includes a JUnit 5 multi-threaded stress test:

```java
@Test
void testConcurrentSeatHold_OnlyOneSucceeds() throws InterruptedException {
    int numberOfThreads = 10;
    ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger conflictCount = new AtomicInteger(0);

    for (int i = 0; i < numberOfThreads; i++) {
        final String user = "fan-" + i;
        executor.submit(() -> {
            try {
                latch.await();
                seatService.lockSeat(targetSeatId, user);
                successCount.incrementAndGet();
            } catch (OptimisticLockingFailureException | SeatUnavailableException e) {
                conflictCount.incrementAndGet();
            }
        });
    }

    latch.countDown(); // Release all 10 threads simultaneously
    executor.shutdown();
    executor.awaitTermination(5, TimeUnit.SECONDS);

    assertEquals(1, successCount.get(), "Exactly one user must acquire the seat");
    assertEquals(9, conflictCount.get(), "Nine concurrent users must receive a 409 conflict");
}
```

---

## 🌐 Zero-Cost Cloud Infrastructure Architecture

| Layer | Provider | Free Tier Specification |
|---|---|---|
| **Edge CDN & Frontend** | **Vercel** | Global Anycast Edge CDN, SSL/TLS, sub-10ms static delivery |
| **Application Runtime** | **Render** | Docker Container runtime (512MB RAM, automatic health probes) |
| **Relational Database** | **Neon** | Serverless PostgreSQL 16 on AWS us-east-2, pooling over TLS |
| **Distributed Cache** | **Upstash** | Serverless In-Memory Redis with encrypted TLS connectivity |

---

## 👨‍💻 Author

**Kiran Kumar Behera**  
- **Role:** Full-Stack / Backend Engineer  
- **GitHub:** [@KiranKumarBehera](https://github.com/KiranKumarBehera)  
- **LinkedIn:** [linkedin.com/in/kirankumarbehera](https://linkedin.com)

---

## 📄 License
This project is open-source and available under the [MIT License](LICENSE).
