# Operator Guide

## First Deployment
1. Register operator, venues and event-vendors; independently confirm
   each venue's business registration + organizer license and each
   vendor's registration before seeding `eventops.store`.
2. Import existing booking/exhibitor/attendance, venue-scheduling and
   vendor-order history.
3. Run read-only event-record-logging and venue-operation dry-runs
   (Phase 0-1).
4. Configure the rollout phase and the `coordinate-vendor-order`
   cost-escalation threshold for human sign-off paths.
5. Publish a dry-run safety-concern flag and audit export.

## Minimum Production Controls
- venue-registration/verification check (business registration +
  organizer license) before ANY proposal for that venue
- vendor-registration/verification check before ANY `:coordinate-
  vendor-order` proposal
- governor gate on every proposal before commit
- human sign-off for `:flag-safety-concern` (always) and high-cost
  `:coordinate-vendor-order` proposals
- audit export for every commit, hold and approval
- backup manual back-office process
- this actor NEVER finalizes a venue-occupancy-limit override or a
  fire-code/emergency-egress-compliance clearance -- that authority
  stays with the venue's own licensed fire-marshal/safety-official
  process, outside this system

## Certification
Certified operators must prove venue/vendor-verification discipline,
governor-bypass resistance, evidence-backed safety-concern reporting and
human review for every escalation-gated action.
