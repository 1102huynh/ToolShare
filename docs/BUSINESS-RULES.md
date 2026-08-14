# ToolShare — Business Rules

## 1. Booking Rules

### BR-BOOKING-001 — Prevent overlapping active bookings

**Trigger:** Booking request creation  
**Condition:** Existing booking overlaps with active states  
**Expected:** Reject with conflict error  
**Test:** Parallel/concurrency booking test

### BR-BOOKING-002 — Listing must be bookable

**Trigger:** Booking request  
**Condition:** Listing is not ACTIVE or policy-restricted  
**Expected:** Reject request  
**Test:** Listing-status matrix

### BR-BOOKING-003 — Valid booking time

**Trigger:** Booking request validation  
**Condition:** Invalid range or start time in the past  
**Expected:** Validation error  
**Test:** Boundary/timezone tests

### BR-BOOKING-004 — Owner approval

**Trigger:** Booking request  
**Condition:** `booking_policy = OWNER_APPROVAL`  
**Expected:** Move to REQUESTED and await owner decision  
**Test:** SLA and approval/rejection tests

### BR-BOOKING-005 — Auto-expire

**Trigger:** Booking pending timer  
**Condition:** Owner does not decide before SLA  
**Expected:** Transition to EXPIRED  
**Test:** Scheduled job/state-transition tests

---

## 2. Payment Rules

### BR-PAYMENT-001 — Webhook idempotency

**Trigger:** Webhook received  
**Condition:** Duplicate provider event ID  
**Expected:** No duplicate financial mutation  
**Test:** Replay webhook

### BR-PAYMENT-002 — Valid capture transition

**Trigger:** Payment capture  
**Condition:** Current payment state does not allow capture  
**Expected:** Reject transition  
**Test:** Payment state tests

### BR-PAYMENT-003 — Payment required for confirmation

**Trigger:** Booking confirmation  
**Condition:** Payment not captured  
**Expected:** Block confirmation  
**Test:** Workflow integration test

### BR-PAYMENT-004 — Refund policy

**Trigger:** Cancellation  
**Condition:** Policy allows refund  
**Expected:** Create refund transaction and update payable  
**Test:** Refund correctness

---

## 3. Deposit Rules

### BR-DEPOSIT-001 — Deposit held before pickup

**Trigger:** Pickup readiness  
**Condition:** Deposit is not HELD  
**Expected:** Block rental start  
**Test:** Rental precondition test

### BR-DEPOSIT-002 — Release after return and claim window

**Trigger:** Rental completion timer  
**Condition:** No open dispute/claim  
**Expected:** Deposit becomes RELEASED  
**Test:** Timed release test

### BR-DEPOSIT-003 — Deduction requires claim workflow

**Trigger:** Deduction request  
**Condition:** Missing evidence, invalid window, or unresolved dispute  
**Expected:** Reject deduction  
**Test:** Negative scenarios

### BR-DEPOSIT-004 — Immutable deposit ledger

Every deposit state mutation must write an immutable transaction record.

---

## 4. Rental Rules

### BR-RENTAL-001 — Rental start prerequisites

Rental starts only when:

- Booking is confirmed.
- Payment checks pass.
- Deposit checks pass.
- Pickup is confirmed.

### BR-RENTAL-002 — Late fee

Late return fees are computed deterministically.

MVP:

- 60-minute grace period.
- Prorated hourly fee.
- Daily cap after threshold.

### BR-RENTAL-003 — No-show

No-show handling applies only after the configured wait window.

---

## 5. Dispute Rules

### BR-DISPUTE-001 — Damage reporting window

Damage claim is allowed only within the configured reporting window.

MVP window: 24 hours after return confirmation.

### BR-DISPUTE-002 — Resolution audit

Admin dispute resolution must record:

- Resolver
- Rationale
- Resolution type
- Financial decision where applicable

### BR-DISPUTE-003 — Financial adjustment sequencing

Financial adjustments are allowed only after the dispute resolution outcome.

---

## 6. Review Rules

### BR-REVIEW-001 — Completed rental required

Reviews are allowed only after booking/rental completion.

### BR-REVIEW-002 — Duplicate prevention

The same actor cannot submit duplicate reviews for the same booking/target pair.

---

## 7. Security Rules

### BR-SEC-001 — Exact pickup address privacy

Before booking confirmation and payment/deposit eligibility, only masked/approximate location is returned.

Exact address is available only to authorized participants after the required booking state.

---

## 8. Admin Rules

### BR-ADMIN-001 — Sensitive action audit

Sensitive admin actions require an audit entry. The operation should fail if the mandatory audit write cannot be established.

---

## 9. Risk Rules

### BR-RISK-001 — Manual review

High-risk transactions are routed to manual review.

Triggers include:

- High booking value.
- New user + high deposit.
- Multiple failed payments.
- Repeated cancellations/no-shows/disputes.
- High booking velocity.
- Location/device/payment anomalies.

Expected behavior:

- Set `review_required`.
- Pause progression.
- Route to admin risk-review queue.

---

## 10. Pricing and Commission Rules

### Commission

- Default platform commission: 12%.
- Category-specific overrides are supported.
- Future promotion overrides may be added.
- Commission policy is resolved before booking confirmation.
- Pricing is snapshotted on the booking.

### Monetary representation

Use money in minor units plus currency.

Do not use floating-point monetary calculations.

### Pricing components

The booking pricing model distinguishes:

- Rental subtotal
- Delivery fee
- Platform fee/commission
- Insurance fee
- Discount
- Tax
- Security deposit
- Total due
- Owner payout

---

## 11. Cancellation Rules

### Renter

- Before owner confirmation: free.
- More than 24h before start: low/no fee.
- Within 24h: fixed or percentage fee.
- After rental starts: no refund except approved dispute.

### Owner

- Before pickup: full renter refund + goodwill credit.
- Repeated cancellations can lead to reliability penalties/suspension.

---

## 12. No-show Rules

### Renter

- 30-minute pickup window.
- Owner records no-show with check-in evidence.
- No-show fee applied.
- Booking cancelled.

### Owner

- Renter reports after wait window.
- Full refund plus compensation credit.
- Owner reliability penalty.

---

## 13. Damage Rules

Required evidence:

- Photos/videos
- Description
- Timestamp
- Estimated damage amount

Workflow:

```text
Claim Submitted
      ↓
Counter Response
      ↓
Admin Decision
      ↓
Deduction / Refund
```

Arbitrary deposit deductions are not allowed.

---

## 14. Lost Tool Rules

- Renter liability is capped by configured policy.
- Deposit is applied first.
- Remaining collection is a manual support workflow in MVP.
- Future insurance integration may automate this.

---

## 15. Location Rules

- Public listing shows approximate district/ward location.
- Exact pickup address is protected.
- Exact address requires confirmed booking and successful payment/deposit checks.

---

## 16. Identity Verification Rules

- Email verification for all users.
- Phone verification before first booking or first listing activation.
- Step-up verification for high-value/risky activity.

---

## 17. Notification Rules

Notification failure must not block core business transactions.

Use asynchronous delivery with retries and dead-letter handling.

---

## 18. Given / When / Then Acceptance Criteria

### Overlap prevention

**Given:** Listing has confirmed booking 10:00–14:00  
**When:** Another renter requests 12:00–13:00  
**Then:** Booking is rejected with conflict and no payment intent is created.

### Payment idempotency

**Given:** Webhook event E was already processed  
**When:** Provider retries event E  
**Then:** No duplicate payment/deposit/booking transition is created.

### Deposit release

**Given:** Rental completed and no dispute within 24h window  
**When:** Release scheduler runs  
**Then:** Deposit transitions to RELEASED and a ledger entry is written.

### Damage deduction guard

**Given:** Owner submits claim without required evidence  
**When:** Deduction is requested  
**Then:** System rejects deduction and deposit remains HELD/RELEASE_PENDING.

### Address privacy

**Given:** Booking is not confirmed  
**When:** Renter requests exact pickup address  
**Then:** System returns masked location only.

### Cancellation fee

**Given:** Renter cancels 8 hours before start  
**When:** Cancellation is processed  
**Then:** Policy fee is applied and reflected in refund breakdown.

### No-show

**Given:** Renter fails to appear within pickup window  
**When:** Owner marks no-show with required evidence  
**Then:** Booking transitions according to no-show policy and charges/refunds are executed.

### Review eligibility

**Given:** Booking is IN_RENTAL  
**When:** User submits review  
**Then:** Review is rejected as not eligible.
