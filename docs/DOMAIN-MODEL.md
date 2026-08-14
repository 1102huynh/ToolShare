# ToolShare — Domain Model

## 1. Identity Domain

### Aggregate

`IdentityAccount`

### Entities

- Credential
- SessionToken
- VerificationChallenge

### Value Objects

- EmailAddress
- PhoneNumber
- PasswordHash
- VerificationStatus

### Rules

- Verified email required for login completion.
- Phone verification gates first transaction/listing activation.

### State

```text
PENDING_VERIFICATION
        ↓
      ACTIVE
        ↓
    SUSPENDED
        ↓
   DEACTIVATED
```

### Events

- AccountRegistered
- EmailVerified
- PhoneVerified
- AccountSuspended
- PasswordResetRequested

---

## 2. User Domain

### Aggregate

`UserProfile`

### Entities

- UserRoleAssignment
- TrustProfile

### Value Objects

- PublicProfile
- ReputationMetrics
- PrivacyPreferences

### Rules

- A single user may act as OWNER and RENTER.
- Sensitive fields are hidden according to access policy.

### Trust state

```text
BASIC → VERIFIED → HIGH_TRUST → RESTRICTED
```

### Events

- ProfileUpdated
- RoleGranted
- RoleRevoked
- TrustLevelChanged

---

## 3. Tool Listing Domain

### Aggregate

`ToolListing`

### Entities

- ToolImage
- ListingPolicy
- ListingLocationSnapshot

### Value Objects

- MoneyRate
- ToolCondition
- DeliveryOption
- ListingStatus

### Rules

- ACTIVE listing still requires availability checking.
- Categories and business logic are configuration-driven.

### State

```text
DRAFT
  ↓
PENDING_REVIEW
  ↓
ACTIVE
  ↓
PAUSED / SUSPENDED / ARCHIVED
```

### Events

- ListingCreated
- ListingActivated
- ListingPaused
- ListingSuspended
- ListingUpdated

---

## 4. Availability Domain

### Aggregate

`AvailabilityCalendar`

### Entities

- AvailabilityRule
- BlockedPeriod
- MaintenanceWindow

### Value Objects

- TimeRange
- RecurrenceRule
- AvailabilityResult

### Rules

- No overlapping confirmed/running bookings.
- Blocked periods supersede open availability.

### Events

- PeriodBlocked
- PeriodUnblocked
- MaintenanceScheduled
- AvailabilityRecomputed

---

## 5. Booking Domain

### Aggregate

`Booking`

### Entities

- BookingPricingSnapshot
- BookingTimelineEvent
- BookingParticipantSnapshot

### Value Objects

- BookingState
- BookingPolicy
- CancellationOutcome

### Rules

- Booking creation must be concurrency-safe.
- State transitions are event-driven only.
- Pricing snapshot becomes immutable once confirmed.

### State

```text
REQUESTED
   ↓
PAYMENT_PENDING
   ↓
CONFIRMED
   ↓
READY_FOR_PICKUP
   ↓
IN_RENTAL
   ↓
RETURN_PENDING
   ↓
COMPLETED
```

Side states:

```text
CANCELLED
EXPIRED
DISPUTED
REJECTED
```

### Events

- BookingRequested
- BookingConfirmed
- BookingRejected
- BookingCancelled
- BookingExpired
- PickupReady
- RentalStarted
- ReturnInitiated
- BookingCompleted
- BookingDisputed

---

## 6. Rental Domain

### Aggregate

`RentalExecution`

### Entities

- PickupRecord
- ReturnRecord
- HandoverChecklist
- LateFeeAssessment

### Value Objects

- HandoverMethod
- ReturnCondition
- DelayDuration

### Rules

- Rental starts only after pickup confirmation and payment/deposit checks.
- Late fee logic must be deterministic and auditable.

### State

```text
READY_FOR_PICKUP
   ↓
IN_RENTAL
   ↓
RETURN_PENDING
   ↓
COMPLETED
```

Exceptional:

- NO_SHOW_RENTER
- NO_SHOW_OWNER

### Events

- PickupConfirmed
- ReturnConfirmed
- LateFeeApplied
- NoShowRecorded

---

## 7. Payment Domain

### Aggregate

`PaymentOrder`

### Entities

- PaymentIntent
- PaymentTransaction
- RefundTransaction
- WebhookEventRecord

### Value Objects

- Money
- PaymentStatus
- ProviderReference
- IdempotencyKey

### Rules

- All provider callbacks are idempotent.
- Booking cannot complete without confirmed payment state.

### State

```text
INITIATED
   ↓
AUTHORIZED
   ↓
CAPTURED
   ↓
SETTLED
```

Side states:

- FAILED
- CANCELLED
- REFUNDED
- PARTIALLY_REFUNDED

### Events

- PaymentIntentCreated
- PaymentAuthorized
- PaymentCaptured
- PaymentFailed
- PaymentRefunded
- WebhookProcessed

---

## 8. Deposit Domain

### Aggregate

`DepositCase`

### Entities

- DepositHold
- DepositAdjustment
- DepositTransaction

### Value Objects

- DepositStatus
- DeductionReason
- ReleasePolicy

### Rules

- Deposit is not revenue.
- Any deduction requires a validated adjustment trail.

### State

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

Side states:

- PARTIALLY_DEDUCTED
- FULLY_DEDUCTED
- DISPUTED

### Events

- DepositHeld
- DepositReleaseScheduled
- DepositReleased
- DepositDeducted
- DepositDisputed

---

## 9. Review Domain

### Aggregate

`ReviewRecord`

### Entities

- RatingDimension
- ReviewModerationFlag

### Value Objects

- StarRating
- ReviewTargetType

### Rules

- Review only after completed booking.
- One review per actor/booking/target pair.

### State

```text
SUBMITTED → PUBLISHED
```

Side states:

- FLAGGED
- HIDDEN

### Events

- ReviewSubmitted
- ReviewPublished
- ReviewFlagged
- ReviewHidden

---

## 10. Dispute Domain

### Aggregate

`DisputeCase`

### Entities

- DamageReport
- EvidenceItem
- ResolutionDecision

### Value Objects

- DisputeType
- ClaimAmount
- ResolutionType
- SLAWindow

### Rules

- Damage claims only within reporting window.
- Deduction requires resolution path and audit.

### State

```text
OPEN
 ↓
UNDER_REVIEW
 ↓
WAITING_PARTY_RESPONSE
 ↓
RESOLVED
```

Side states:

- REJECTED
- ESCALATED

### Events

- DisputeOpened
- EvidenceAdded
- ResponseSubmitted
- DisputeResolved
- DisputeEscalated

---

## 11. Notification Domain

### Aggregate

`NotificationPlan`

### Entities

- NotificationMessage
- DeliveryAttempt
- TemplateBinding

### Value Objects

- Channel
- NotificationStatus
- RetryPolicy

### Rules

Notification failure cannot block critical transaction commits.

### State

```text
PENDING → SENT
```

Failure path:

```text
FAILED → RETRYING → DEAD_LETTER
```

### Events

- NotificationQueued
- NotificationSent
- NotificationFailed
- NotificationRetried

---

## 12. Admin Domain

### Aggregate

`AdminActionCase`

### Entities

- ModerationDecision
- RiskReview
- AuditEntry

### Value Objects

- ActionType
- ReasonCode
- ActorContext

### Rules

- Sensitive actions require audit records.
- Dual control may be added in the future.

### State

```text
CREATED → APPROVED → EXECUTED
```

Side states:

- REJECTED
- REVOKED

### Events

- AdminActionInitiated
- AdminActionApproved
- AdminActionExecuted

---

## 13. Shared Value Objects

- Money: amount in minor units + currency.
- DateTimeRange: start/end + timezone-safe validation.
- GeoPoint.
- ServiceRadius.
- PricingBreakdown.
- CommissionPolicy.
- IdempotencyKey.
- VerificationState.

---

## 14. Core Domain Events

- BookingCreated
- BookingConfirmed
- PaymentCaptured
- DepositHeld
- RentalStarted
- RentalCompleted
- DepositReleased
- DisputeOpened
- DisputeResolved
- ReviewSubmitted
