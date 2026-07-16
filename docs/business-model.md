# Business Model: Convention/Trade-Show Event-Operations Coordination

## Classification
- Repository: `cloud-itonami-isic-8230`
- ISIC Rev.5: `8230` -- organization of conventions and trade shows
  (event-planning/venue-booking coordination for large public
  gatherings)
- Social impact: public safety, local economy, transparency

## Customer
- independent convention/trade-show organizers needing an auditable
  operations-coordination platform
- multi-venue operators needing consistent venue-scheduling/vendor-
  order/safety governance across events
- programs that cannot accept closed, unauditable back-office platforms

## Offer
- booking/exhibitor/attendance data logging
- venue-booking/floor-plan/logistics scheduling coordination
- event-vendor (catering, AV, security staffing) procurement
  coordination with registered, verified vendors
- crowd-safety/venue-occupancy-limit concern flagging (fire-code
  capacity, emergency-egress-planning observations) for human triage
- role-based access and immutable audit ledger

## Revenue
- self-host setup fee
- managed hosting subscription per venue/event
- support retainer with SLA

## Trust Controls
- `:event-operations-governor` never lets a proposal for an
  unregistered/unverified venue, or a vendor order naming an
  unregistered/unverified event-vendor, commit or even escalate
- every proposal's `:effect` must be `:propose` -- a claim to directly
  actuate is a HARD, un-overridable block
- directly finalizing a venue-occupancy-limit override, or directly
  finalizing/issuing a fire-code or emergency-egress-compliance
  clearance, is permanently out of scope, not a rollout milestone -- the
  actor may only flag a concern for a human
- a `:flag-safety-concern` proposal, and a high-cost `:coordinate-
  vendor-order`, always require human sign-off
- sensitive attendee, exhibitor and vendor data stays outside Git
