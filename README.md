# ⚡ FlashPass — Distributed High-Concurrency Booking Engine

[![Live Demo](https://img.shields.io/badge/Demo-Live%20on%20Vercel-success?style=for-the-badge&logo=vercel)](https://vercel.com)
[![Backend](https://img.shields.io/badge/API-Render%20Cloud-blue?style=for-the-badge&logo=render)](https://render.com)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3%20%2F%20Java%2017-brightgreen?style=for-the-badge&logo=springboot)](https://spring.io)
[![React](https://img.shields.io/badge/React%2019-Vite%20SPA-61DAFB?style=for-the-badge&logo=react)](https://react.dev)
[![Docker](https://img.shields.io/badge/Docker-Multi--Stage%20Alpine-2496ED?style=for-the-badge&logo=docker)](https://docker.com)
[![Redis](https://img.shields.io/badge/Upstash-Cloud%20Redis%20TLS-DC382D?style=for-the-badge&logo=redis)](https://upstash.com)
[![PostgreSQL](https://img.shields.io/badge/Neon-Cloud%20PostgreSQL-336791?style=for-the-badge&logo=postgresql)](https://neon.tech)

> **FlashPass** is an enterprise-grade, high-throughput distributed ticket reservation engine engineered to eliminate race conditions, double-booking, and seat hoarding during flash-sale traffic spikes (e.g., stadium concert tours). Built with **Spring Boot 3.3**, **React 19**, **Neon PostgreSQL**, **Upstash Redis**, **WebSockets (STOMP)**, and containerized with **Multi-Stage Docker**.

---

## 🏗️ System Architecture

```
                                [ CLIENT LAYER ]
                     Web / Mobile Browsers (Vercel CDN Edge)
                                       │
                    ┌──────────────────┴──────────────────┐
                    │ HTTPS REST APIs                     │ WSS Native WebSocket (STOMP)
                    ▼                                     ▼
        ┌─────────────────────────────────────────────────────────────┐
        │                 FLASHPASS ENGINE (Render)                   │
        │               Spring Boot 3.3 / Java 17 LTS                 │
        │                                                             │
        │  ┌────────────────────────┐     ┌────────────────────────┐  │
        │  │  Global Exception Ctr  │     │   STOMP Simple Broker  │  │
        │  │  @RestControllerAdvice │     │   /topic/seats PubSub  │  │
        │  └───────────┬────────────┘     └───────────▲────────────┘  │
        │              │                              │               │
        │  ┌───────────▼────────────┐                 │               │
        │  │     SeatService        ├─────────────────┘               │
        │  │  @Transactional Lease  │ (Broadcasts Mutation in <30ms)  │
        │  └─────┬──────────────┬───┘                                 │
        └────────┼──────────────┼─────────────────────────────────────┘
                 │              │
      Cache-Aside (TTL 10m)     │ Atomic Optimistic Locking (@Version)
                 ▼              ▼
     ┌──────────────────────┐ ┌──────────────────────────┐
     │ Upstash Cloud Redis  │ │   Neon PostgreSQL Cloud  │
     │ In-Memory Read Cache │ │   ACID Relational Store  │
     └──────────────────────┘ └──────────────────────────┘
```

---

## ✨ Key Engineering Highlights

1. **JPA Optimistic Locking (`@Version`):**
   - Solves concurrency collisions without heavy pessimistic table locks.
   - Verified via JUnit 5 multithreaded stress testing (`10 concurrent threads` racing for the same seat $\rightarrow$ 1 winner, 9 caught `ObjectOptimisticLockingFailureException` returning HTTP 409 Conflict).

2. **Cache-Aside Pattern with Redis:**
   - Database read latency reduced from **~400ms to <10ms** using Upstash Redis.
   - Declarative caching with Spring `@Cacheable(value = "eventSeats")` and automatic cluster invalidation with `@CacheEvict(allEntries = true)` upon state mutations.

3. **Sub-30ms Real-Time Push (WebSockets & STOMP):**
   - Pure RFC 6455 native WebSocket transport (`/ws-flashpass`) broadcasting seat holds, releases, and purchases across concurrent browser sessions instantly without polling.

4. **Multi-Stage Production Docker Packaging:**
   - **Backend:** Maven JDK builder $\rightarrow$ Alpine JRE 17 runtime (**~150MB** image with non-root security user and JVM container flags).
   - **Frontend:** Node 20 builder $\rightarrow$ Nginx Alpine runtime (**~25MB** image with gzip compression and SPA client-side routing).

5. **Seat Hold State Machine & TTL Lease:**
   - Enforces linear state transitions: `AVAILABLE` $\rightarrow$ `LOCKED` (5-minute cart lease) $\rightarrow$ `BOOKED` (paid ticket) or back to `AVAILABLE`.

---

## 📁 Monorepo Structure

```
flashpass/
├── flashpass-engine/            # Spring Boot 3.3 Backend API & WebSocket Engine
│   ├── src/main/java/...        # Controllers, Services, Entities, Handlers
│   ├── src/main/resources/      # application.properties (Cloud dynamic PORT)
│   ├── Dockerfile               # Multi-stage Maven -> Alpine JRE 17
│   ├── .dockerignore
│   └── pom.xml
│
├── flashpass-ui/                # React 19 + Vite Frontend Single Page Application
│   ├── src/                     # Stadium seating UI, STOMP client, HUD metrics
│   ├── Dockerfile               # Multi-stage Node 20 -> Nginx Alpine
│   ├── nginx.conf               # Nginx reverse proxy & SPA try_files
│   ├── .env.example             # Production API & WSS URL templates
│   └── package.json
│
├── docker-compose.yml           # Unified multi-container local & staging orchestration
├── .gitignore                   # Clean exclusion rules
└── README.md                    # Project documentation
```

---

## 🚀 Running Locally

### Option A: Using Docker Compose (Recommended)
```bash
docker-compose up --build
```
- Frontend UI: `http://localhost:80`
- Backend REST API: `http://localhost:8080/api/events`

### Option B: Running Standalone

**1. Start Backend:**
```bash
cd flashpass-engine
mvn clean spring-boot:run
```

**2. Start Frontend:**
```bash
cd flashpass-ui
npm install
npm run dev
```

---

## 🌐 Cloud Infrastructure (100% Free Forever)

| Component | Cloud Provider | Tier | Role |
| :--- | :--- | :--- | :--- |
| **Frontend** | [Vercel](https://vercel.com) | Free Hobby | Edge CDN static hosting, HTTPS, WSS |
| **Backend** | [Render](https://render.com) | Free Web Service | Docker containerized Spring Boot runtime |
| **Database** | [Neon](https://neon.tech) | Free Serverless | AWS us-east-2 PostgreSQL over TLS |
| **Distributed Cache** | [Upstash](https://upstash.com) | Free Serverless | In-Memory Redis with encrypted TLS |

---

## 👨‍💻 Author

**Kiran Kumar Behera**  
- **Role:** Full-Stack / Backend Engineer  
- **LinkedIn:** [linkedin.com/in/kirankumarbehera](https://linkedin.com)  
- **GitHub:** [@KiranKumarBehera](https://github.com/KiranKumarBehera)
