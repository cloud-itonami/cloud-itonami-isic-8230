# Governance

`cloud-itonami-isic-8230` is an OSS open-business blueprint for
convention/trade-show event-operations coordination (ISIC Rev.5 8230 --
organization of conventions and trade shows).

## Maintainers
Maintainers may merge changes that preserve these invariants:
- a proposal for an unverified/unregistered venue, or a vendor order
  naming an unverified/unregistered event-vendor, can never commit.
- the EventOperationsGovernor remains independent of the advisor.
- hard policy violations (non-`:propose` effect, venue-occupancy-limit-
  override or fire-code/egress-compliance-clearance finalization
  content, an op outside the closed allowlist) cannot be overridden by
  human approval.
- a proposal that directly finalizes a venue-occupancy-limit override or
  a fire-code/emergency-egress-compliance clearance is always a hard,
  permanent block -- never auto-commit-eligible, never approvable past.
- `:flag-safety-concern` always escalates to a human and is never a
  member of any rollout phase's `:auto` set.
- every event-record log, venue-operation schedule, vendor-order
  coordination and safety-concern flag is auditable.
- attendee, exhibitor and vendor data stays outside Git.

## Decision Records
Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or
license should add or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification is
a separate trust mark and should require security, audit and data-flow
review.

Certified operators can lose certification for:
- bypassing event-record, venue-operation, vendor-order or safety-
  concern policy checks
- mishandling attendee, exhibitor or vendor data
- misrepresenting certification status
- failing to respond to security or crowd-safety incidents
