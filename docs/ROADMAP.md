# ToolShare — Development Roadmap

## Phase 0 — Product and Architecture Finalization

Objectives:

- Close critical business questions.
- Freeze MVP scope.
- Freeze policies.
- Establish domain model.
- Establish architecture.
- Establish business rules.
- Establish API/database blueprint.

Status from the Copilot analysis:

- Product and technical blueprint finalized at analysis level.
- No application code written.
- No migrations created.
- No controllers/services created.
- No dependencies installed.

## Phase 1 — Project Bootstrap

### T-001 — Module skeleton

**Objective:** Establish module skeleton and shared conventions.

**Dependencies:** None

**Modules:**

- common
- identity
- user
- listing
- availability
- booking
- rental
- payment
- deposit
- review
- dispute
- notification
- admin

**Acceptance:** Project compiles with empty modules and quality gates.

### T-002 — Shared infrastructure

Implement:

- Standard error model.
- Request ID.
- Audit context plumbing.
- Global exception model.

**Dependencies:** T-001

**Tests:** API error format tests.

---

## Phase 2 — Identity and User Profile

### T-003 — Identity baseline

Implement:

- Register
- Login
- Email verification

**Dependencies:** T-001, T-002

**Database:**

- users
- user_roles
- user_verifications

**Tests:** Authentication unit/API tests.

### T-004 — Roles and permissions

Implement role/permission enforcement.

Roles:

- RENTER
- OWNER
- ADMIN

A user may have both RENTER and OWNER capabilities.

**Tests:** Authorization matrix tests.

---

## Phase 3 — Catalog and Listing

### T-005 — Category and listing core

Implement:

- Categories
- Tool listing CRUD
- Listing state transitions
- Locations

**Database:**

- categories
- tool_listings
- tool_images
- locations

**Tests:** Validation and transition tests.

### T-006 — File storage

Implement:

- FileStorage abstraction
- Image upload
- Upload validation
- MIME/size checks

**Tests:** Upload validation tests.

---

## Phase 4 — Search and Location

### T-007 — Availability engine

Implement:

- Availability rules
- Blocked periods
- Maintenance windows
- Availability calculation

**Tests:** Availability calculation tests.

### T-008 — Search

Implement:

- Keyword
- Category
- City/district
- Price
- Availability
- Delivery
- Pagination
- Sorting

**Tests:** Filter correctness and pagination.

### T-019 — Location privacy

Implement:

- Masked location
- Exact location authorization
- Public/private location projections

**Tests:** Masked/unmasked authorization tests.

---

## Phase 5 — Availability and Booking Core

### T-009 — Booking aggregate

Implement:

- Booking creation
- Booking retrieval
- Approve
- Reject
- Cancel
- State machine

**Business rules:** BR-BOOKING-001..005

**Database:**

- bookings
- booking_status_history

### T-010 — Booking concurrency

Implement:

- Transaction-safe overlap prevention
- Lock-aware queries

**Tests:**

- Parallel booking requests.
- Same listing/time-window race conditions.

---

## Phase 6 — Payment and Deposit

### T-011 — Payment abstraction

Implement:

- PaymentProvider interface
- Payment intent
- Payment status
- Payment transactions

**Database:**

- payments
- payment_transactions

### T-012 — Webhooks

Implement:

- Signature verification
- Idempotency
- Replay protection
- Duplicate event handling
- Out-of-order event handling

**Database:**

- idempotency_keys
- provider event uniqueness

### T-013 — Deposit

Implement:

- Deposit aggregate
- Hold
- Release
- Deduction
- Deposit ledger

**Database:**

- deposits
- deposit_transactions

**Tests:**

- Hold/release/deduction state transitions.
- Immutable ledger behavior.

---

## Phase 7 — Rental Lifecycle

### T-014 — Rental execution

Implement:

- Pickup confirmation
- Return confirmation
- No-show
- Late fee
- Rental completion

**Business rules:** BR-RENTAL-001..003

**Tests:**

- Late return.
- No-show.
- Pickup prerequisites.

---

## Phase 8 — Reviews and Disputes

### T-015 — Dispute and damage

Implement:

- Create dispute
- Damage report
- Evidence upload
- Renter response
- Admin resolution
- Financial adjustment

**Business rules:** BR-DISPUTE-001..003

**Tests:**

- Reporting window.
- Evidence requirements.
- Resolution sequencing.

### T-016 — Reviews

Implement:

- Renter -> owner/tool review
- Owner -> renter review
- Review eligibility
- Duplicate prevention
- Moderation

**Tests:**

- Completed rental requirement.
- Duplicate review prevention.

---

## Phase 9 — Notifications and Admin

### T-017 — Notification outbox

Implement:

- Outbox events
- Worker
- Email/in-app notification
- Retry
- Dead-letter handling

**Tests:**

- Outbox atomicity.
- Retry.
- Dead-letter.

### T-018 — Admin risk/moderation

Implement:

- Risk review queue
- Listing moderation
- User moderation
- Dispute resolution
- Audit logs

**Tests:**

- Admin authorization.
- Audit requirements.

---

## Phase 10 — Pricing, Tax, and Financial Hardening

### T-020 — Commission and tax architecture

Implement/finalize:

- Commission policy
- Category override
- Pricing snapshot
- Tax line items
- Invoice metadata
- Owner payout calculations

Default commission:

**12%**

**Tests:**

- Deterministic pricing.
- Commission calculations.
- Snapshot consistency.

---

## Phase 11 — Testing Hardening

### T-021 — End-to-end critical path

Critical path:

```text
Register
  ↓
Verify
  ↓
Create Listing
  ↓
Search
  ↓
Request Booking
  ↓
Pay
  ↓
Pickup
  ↓
Return
  ↓
Release Deposit
  ↓
Review
```

Also test:

```text
Damage
  ↓
Dispute
  ↓
Admin Resolution
```

### T-022 — Security hardening

Implement/test:

- Authentication security
- Authorization
- Rate limiting
- Brute-force protection
- Object-level authorization
- File validation
- SQL injection defenses
- XSS defenses
- Security regression suite

---

## Phase 12 — Observability and Release Readiness

### T-023 — Operations readiness

Implement:

- Health/readiness endpoints
- Metrics
- Request/trace IDs
- Logging
- Audit visibility
- Backup policy

### T-024 — Documentation freeze

Finalize:

- Product documentation
- Architecture documentation
- Business rules
- API contract
- Database schema documentation
- Traceability from business rules to tests

---

# Recommended Implementation Order

```text
T-001
  ↓
T-002
  ↓
T-003
  ↓
T-004
  ↓
T-005
  ↓
T-006
  ↓
T-007
  ↓
T-008
  ↓
T-009
  ↓
T-010
  ↓
T-011
  ↓
T-012
  ↓
T-013
  ↓
T-014
  ↓
T-015 / T-016
  ↓
T-017 / T-018
  ↓
T-019 / T-020
  ↓
T-021
  ↓
T-022
  ↓
T-023
  ↓
T-024
```

Each task should be implemented independently, tested, reviewed, and approved before moving to the next major task.

# Current Starting Point

The blueprint is ready for implementation.

The immediate next task is:

**T-001 — Establish module skeleton and shared conventions.**
