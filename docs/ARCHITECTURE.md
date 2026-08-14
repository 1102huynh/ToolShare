# ToolShare — Architecture

## 1. Architecture Style

**Recommended architecture: Modular Monolith**

### Reasons

- Fits MVP speed and startup cost constraints.
- Easier transaction consistency for booking/payment/deposit.
- Lower operational complexity than microservices.
- Allows later extraction by module boundaries.

## 2. Bounded Contexts / Modules

- Identity
- User Profile
- Catalog and Listing
- Availability
- Booking and Rental
- Payment
- Deposit
- Review
- Dispute
- Notification
- Admin and Audit
- Shared: money, time range, geo, idempotency, enums, error model

### Backend module boundaries

```text
identity
user
listing
catalog
availability
booking
rental
payment
deposit
review
dispute
notification
admin
common
```

## 3. Dependency Rules

- Controllers depend on application services.
- Controllers never access repositories directly.
- Application services coordinate aggregates.
- Domain rules stay in domain layer.
- Payment and deposit do not directly mutate booking state without domain events/commands.
- Notification subscribes through outbox; no synchronous coupling.
- Admin accesses domain interfaces and requires audit context.

## 4. Technology Stack

### Backend

- Java 21
- Spring Boot
- Spring Web
- Spring Security
- Spring Validation
- Spring Data JPA
- PostgreSQL
- Flyway
- JUnit 5
- Mockito
- Testcontainers

### Frontend

- Next.js App Router
- React
- TypeScript
- Feature-oriented structure by business domain
- Typed API contracts
- Minimal state libraries
- React Query only when remote-state complexity requires it

## 5. Architectural Patterns

- Application-layer transaction scripts with rich domain rules.
- Outbox pattern for reliable asynchronous notifications/webhook processing.
- Idempotent command handlers for payment callbacks.
- Optimistic locking plus selective pessimistic locking for booking race windows.
- Centralized API error model.
- Request/correlation IDs.
- Provider abstraction for payments.
- FileStorage abstraction for uploaded images/evidence.

## 6. Authentication Architecture

Decision:

- Email/password + verification.
- JWT access tokens.
- Refresh-token rotation.
- Phone verification gate by risk/action.
- Token revocation and device/session tracking.

## 7. Payment Architecture

Use:

```text
PaymentProvider
       |
       +-- MVP Provider Adapter
```

The domain should not be coupled directly to a provider SDK.

Payment callbacks must:

1. Verify signature.
2. Resolve idempotency.
3. Apply one-way state transition.
4. Write immutable transaction rows.
5. Emit outbox event.

## 8. Deposit Architecture

Deposit is a separate aggregate and ledger from rental payment.

Deposit transactions are immutable.

Flow:

```text
REQUESTED
   ↓
AUTHORIZED
   ↓
HELD
   ↓
RELEASE_PENDING
   ↓
RELEASED
```

Side paths:

```text
PARTIALLY_DEDUCTED
FULLY_DEDUCTED
DISPUTED
```

## 9. Booking Concurrency

Booking creation must be transactionally safe.

Flow:

1. Validate listing state and eligibility.
2. Lock availability slice.
3. Re-check overlapping active bookings.
4. Persist booking in `PAYMENT_PENDING`.

The system must reject simultaneous conflicting reservations.

## 10. Notification Architecture

Use:

```text
Business Transaction
      ↓
Outbox Event
      ↓
Worker
      ↓
Notification Delivery
```

Notifications must not block critical transaction commits.

Failed notifications need retry/dead-letter handling.

## 11. File Storage

Use:

```text
FileStorage
   ├── Local adapter (development)
   └── Object-storage-ready adapter
```

Upload pipeline must support:

- MIME validation
- Size limits
- Extension validation
- Security/malware scanning hook point

## 12. Database

PostgreSQL is the primary datastore.

Use migration tooling and explicit indexing strategy.

Financial records require:

- Foreign keys
- Immutable transaction records
- Auditability
- Non-negative monetary constraints
- Idempotency constraints

## 13. API Architecture

REST APIs under:

```text
/api/v1
```

Authentication:

- Bearer access token
- Refresh token
- RBAC for admin

Mutation endpoints involving booking/payment/deposit/dispute/admin operations require `Idempotency-Key`.

Standard error model:

```text
code
message
traceId
timestamp
fieldErrors
```

## 14. Security Architecture

### Authentication

- Argon2id or BCrypt with strong parameters.
- Short-lived access tokens.
- Refresh-token rotation.
- Account lockout/rate limiting.
- Least-privilege RBAC.

### API

- Strict DTO validation.
- Output encoding/sanitization.
- File upload validation.
- CSRF protection when cookie-based authentication is used.
- Secure token approach otherwise.

### Payments

- Verify webhook signatures.
- Idempotent callbacks.
- Replay protection.
- Never trust client-side payment status.

### Data

- Secrets in environment/secret manager.
- No sensitive logging.
- PII minimization.
- Exact-address access control.
- Audit financial/moderation actions.

### Operations

- Security headers.
- CORS policy.
- TLS.
- Dependency scanning.
- SAST.
- Least-privilege DB credentials.
- Backup/restore drills.

## 15. ADR Summary

### ADR-001 — Modular Monolith

Single deployable backend with strong internal module boundaries.

### ADR-002 — PostgreSQL

Chosen for relational integrity, ACID transactions, indexing, and JSON support.

### ADR-003 — Booking Concurrency

Transactional conflict checking with row-level locking and overlap-prevention logic.

### ADR-004 — Payment Provider Abstraction

`PaymentProvider` interface with one MVP adapter.

### ADR-005 — Security Deposit

Separate deposit aggregate and immutable ledger.

### ADR-006 — Authentication

Email/password + verification, JWT access tokens, refresh rotation, phone verification gates.

### ADR-007 — Async Notifications

Outbox + worker asynchronous dispatch.

### ADR-008 — File Storage

FileStorage abstraction with local development adapter and object-storage-ready interface.

## 16. High-Level Deployment Shape

```text
Next.js Frontend
       |
       | REST /api/v1
       v
Spring Boot Modular Monolith
       |
       +---- PostgreSQL
       |
       +---- Payment Provider
       |
       +---- File Storage
       |
       +---- Outbox Worker / Notification Provider
```

The initial platform runs as one backend deployment plus one frontend application.
