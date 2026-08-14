# ToolShare — Product Specification

## 1. Product Overview

**Project name:** ToolShare  
**Product type:** Peer-to-Peer Tool Rental Marketplace

ToolShare is a two-sided rental marketplace with a trust-critical financial lifecycle, not just a listing app.

### Business core

- Supply side: owners list underutilized tools.
- Demand side: renters discover, book, pay, use, return.
- Platform value: trust, reliability, and safe transaction orchestration.

### Primary value loop

Listing liquidity + local search + reliable booking + secure payment/deposit + post-rental trust (reviews/disputes).

### Key business risks

- Double bookings and availability inconsistency.
- Payment/deposit integrity and webhook idempotency.
- Fraud, abuse, and dispute fairness.
- Owner/renter cancellation friction.
- Location privacy and physical safety.

## 2. Business Goals

- Solve the "I only need this expensive tool once or twice" problem.
- Save money for renters and create passive income for tool owners.
- Generate revenue through 10–15% transaction commission plus insurance/deposit-related fees.
- Build a network of local drop-off points.
- Expand into B2B partnerships.
- Target 5,000–10,000 monthly rentals in Ho Chi Minh City in year one.
- Later expand to Hanoi and Da Nang.

## 3. Target Users

### Renter

People who need tools temporarily for:

- Home renovation
- DIY projects
- Repairs
- Gardening
- Construction
- Maintenance
- Odd jobs

### Tool Owner

People who own tools that are not frequently used and want to generate additional income.

### Freelancer / Repairman

Freelance workers or small contracting crews who need specialized tools but do not own every tool required for every job.

### Landlord / Property Owner

Owners of apartments, mini apartments, rental properties, or small buildings who occasionally need maintenance equipment.

### B2B Partner

Potential partners:

- Hardware stores
- Building material stores
- Tool shops
- Repair businesses
- Construction companies
- Freelance repair crews

## 4. Core User Journeys

### Renter journey — MVP critical

1. Register and verify account.
2. Search by category/location/date range.
3. View listing details, owner rating, pricing breakdown, deposit.
4. Request booking or instant book depending on policy.
5. Pay rental amount and authorize/hold deposit.
6. Pickup/delivery handoff.
7. Return tool.
8. Deposit release or dispute flow.
9. Submit review.

### Owner journey — MVP critical

1. Register and verify account.
2. Create listing with images, pricing, deposit, availability windows, pickup/delivery settings.
3. Approve/auto-confirm booking based on policy.
4. Handoff tool.
5. Confirm return condition or raise damage claim.
6. Receive payout.
7. Review renter.

### Admin journey — MVP critical

1. Moderate users/listings/reviews.
2. Resolve disputes and damage claims.
3. Audit payment/deposit/refund/deduction flows.
4. Manage categories and platform settings.
5. Manage commission rate, thresholds, and flags.

## 5. MVP Scope

### In scope

- Authentication and account verification.
- Unified user profile; one identity can act as renter and owner.
- Tool listing and image upload.
- Category-driven catalog.
- Search/filter by keyword, category, location, availability, and price.
- Availability enforcement with overlap prevention.
- Booking lifecycle with explicit state machine.
- Payment abstraction with one provider adapter.
- Deposit hold/release workflow with auditable ledger entries.
- Basic asynchronous email and in-app notifications.
- Two-way post-rental reviews.
- Basic dispute/damage case creation and admin resolution.
- Admin console/API for moderation and operational controls.
- Audit logging for sensitive operations.

### Out of scope for MVP

- Multi-provider payment support in production.
- Insurance underwriting integrations.
- Full delivery network orchestration.
- Advanced trust scoring/fraud ML.
- Recommendation engine/personalized ranking.
- Complex promotions, subscriptions, and B2B billing.

## 6. Critical MVP Business Decisions

### Booking

Hybrid model:

- Default: owner approval.
- Optional: instant booking for trusted owners/tools.
- Listing field: `booking_policy = OWNER_APPROVAL | INSTANT`.
- Owner response SLA and auto-expiration are required.

### Payment

- Authorize payment method at booking request.
- Capture rental amount at owner confirmation, or immediately for instant bookings.
- Capture before pickup.
- Support two-stage `AUTHORIZED -> CAPTURED`.

### Security deposit

- Hold at booking confirmation.
- Deposit must be held before pickup.
- Automatic release after return and damage window closes.
- If disputed, keep in release-pending/dispute flow.
- Deposit is not platform revenue.

### Cancellation

Renter:

- Before owner confirmation: free.
- More than 24h before start: low/no fee.
- Within 24h: fixed or percentage fee.
- After rental starts: no refund except approved dispute.

Owner:

- Before pickup: full renter refund plus platform goodwill credit.
- Repeated owner cancellations increase penalty/suspension risk.

### Late return

- 60-minute grace period.
- Prorated hourly fee, then daily cap after threshold.
- Deposit may cover unpaid late fees after notice.
- Late fees pass to owner; platform takes normal commission.

### No-show

Renter:

- 30-minute pickup window.
- Owner records no-show with evidence.
- Apply no-show fee and cancel.

Owner:

- Renter may report no-show after wait window.
- Full refund plus compensation credit.
- Owner reliability penalty.

### Damage

Required evidence:

- Photos/videos
- Description
- Timestamp
- Estimated amount

Rules:

- Report within 24h after return confirmation.
- Deduction requires owner claim, renter response window, and admin approval if contested.
- Flow: claim -> counter-response -> admin decision -> deduction/refund.

### Lost tool

- Renter liable up to defined maximum liability.
- Maximum = minimum of replacement-value cap and policy cap.
- Deposit applied first.
- Remaining collection is manual/off-platform in MVP.
- Future: insurance-backed loss coverage.

### Owner payout

Payout after rental completion and dispute window closure, minus commission and adjustments.

### Commission

- Default: 12%.
- Category-specific override supported.
- Future promotion override framework.
- Pricing snapshot persisted at booking creation.

### Delivery

MVP:

- Optional owner-managed delivery.
- Renter pays by default.
- Fixed delivery fee per listing.

### Location privacy

- Approximate district/ward location before booking.
- Exact pickup address only after booking is confirmed and payment/deposit checks pass.

### Identity verification

Baseline:

- Email verification for all users.
- Phone verification before first booking or first listing activation.

Step-up verification for:

- High-value tools/bookings.
- Repeated disputes/no-shows.
- Suspicious risk score.

### Vietnam tax/invoice architecture

- Separate fee and tax lines.
- Store invoice metadata references.
- Export-ready accounting records.
- Do not hardwire one e-invoice provider.

### Fraud/risk manual review

Manual review for:

- Booking value above threshold.
- New user + high deposit.
- Multiple failed payments.
- Repeated cancellations/no-shows/disputes.
- Abnormal booking velocity.
- Location/device/payment anomalies.

## 7. Assumptions

- MVP starts with one city; geography/currency/timezone are configurable.
- Single legal entity operates platform commissions and escrow-like flows.
- One payment provider adapter in MVP with abstraction from day one.
- Booking conflicts are prevented at backend transaction level.
- Email is mandatory in MVP; phone verification can be phased in.
- Delivery remains basic, not route-optimized.
- Admin resolves disputes manually with full audit logs.
- Deposits are tracked separately from rental revenue.
- Notifications are asynchronous and non-blocking.
- Platform starts as one deployable backend plus one frontend app.
