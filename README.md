# cloud-itonami-isic-8230

Open Business Blueprint for **ISIC Rev.5 8230**: organization of
conventions and trade shows -- event-planning/venue-booking coordination
for large public gatherings, carrying a crowd-safety/venue-occupancy-
limit dimension (fire-code capacity limits, emergency-egress planning).

This repository publishes a convention/trade-show
operations-COORDINATION actor -- booking/exhibitor/attendance data
logging, venue-booking/floor-plan/logistics scheduling, event-vendor
(catering, AV, security staffing) procurement coordination with
registered vendors, and crowd-safety/venue-occupancy-limit concern
flagging -- as an OSS business that any qualified operator can fork,
deploy, run, improve and sell, so an independent event organizer never
surrenders its operations data to a closed back-office SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, in-mem/Datomic checkpoints) -- the same actor pattern as
every prior actor in this fleet -- here it is **EventOperationsAdvisor
⊣ EventOperationsGovernor**. This blueprint's own
`:itonami.blueprint/governor` keyword, `:event-operations-governor`, is
a distinct, independent build (confirmed unique via
`org:cloud-itonami` code search at build time).

> **Why an actor layer at all?** An LLM is great at drafting a booking/
> attendance-record summary, a venue-scheduling proposal, or an
> event-vendor procurement request -- but it has no license to actually
> finalize a venue-occupancy-limit override or a fire-code/emergency-
> egress-compliance clearance, no way to independently confirm a venue
> or an event-vendor is actually a registered/verified counterparty, and
> no notion of when a "flag this concern" op quietly turns into a claim
> to have already cleared it. Letting it act directly invites an
> unverified venue's data entering the ledger, an unverified vendor
> receiving a procurement order, or -- worst of all -- a fabricated
> claim to have finalized a crowd-safety clearance, exposing attendees,
> exhibitors and organizers to real liability. This project seals the
> EventOperationsAdvisor into a single node and wraps it with an
> independent **EventOperationsGovernor**, a human **approval
> workflow**, and an immutable **audit ledger**.

## Scope: coordination only, never a safety-clearance authority

This actor is **operations coordination only**. It never performs or
authorizes:

- directly finalizing a venue-occupancy-limit override (letting a crowd
  exceed the posted/fire-code-derived capacity)
- directly finalizing or issuing a fire-code-compliance clearance
- directly finalizing or issuing an emergency-egress-compliance
  clearance/sign-off

The governor's `scope-exclusion-violations` check re-scans every
proposal for this failure mode independently of the advisor's own
framing, and treats it as a HARD, permanent block regardless of
confidence or how clean everything else is. Flagging a crowd-safety/
venue-occupancy-limit concern for a human to triage is exactly this
actor's job -- `:flag-safety-concern` is never excluded by this check,
only FINALIZING/issuing/clearing that concern is, and
`:flag-safety-concern` is never eligible for auto-commit at any rollout
phase (always a hard permanent block or always-escalate op, never
auto-commit-eligible).

### Actuation

**Every proposal this actor generates is `:effect :propose`, never a
direct actuation.** Two independent layers enforce this
(`eventops.governor`'s `effect-not-propose-violations` HARD check and
`eventops.phase`'s phase table, which never puts `:flag-safety-concern`
in any phase's `:auto` set). A human event-operations coordinator is
always the one who actually acts on a flagged concern or confirms a
high-cost vendor order.

## The core contract

```
venue/vendor registration + operations-coordination request
        |
        v
   ┌───────────────────────┐   proposal      ┌────────────────────────────┐
   │ EventOperations-      │ ─────────────▶ │ EventOperationsGovernor      │  (independent system)
   │ Advisor (sealed)      │  + citations    │ venue-unverified ·           │
   └───────────────────────┘                 │ vendor-unverified (NEW) ·    │
          │                 commit ◀┼ effect-not-propose ·                │
          │                         │ scope-excluded (venue-occupancy-     │
    record + ledger        escalate ┼ override / fire-code / egress-       │
          │              (ALWAYS for│ clearance finalization) ·            │
          │       :flag-safety-     │ op-not-allowed                       │
          │       concern/high-cost └────────────────────────────┘
          │       vendor-order
          ▼
      human approval
```

**The EventOperationsAdvisor never commits a proposal the
EventOperationsGovernor would reject, and a safety-concern flag or a
high-cost vendor order never commits without a human sign-off.** Hard
violations (an unregistered/unverified venue; an unregistered/
unverified vendor-order vendor; a non-`:propose` effect; content
touching venue-occupancy-limit-override or fire-code/egress-compliance-
clearance finalization; an op outside the closed allowlist) force
**hold** and *cannot* be approved past.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
may perform physical domain work** (here: exhibitor booth setup,
signage placement, AV rigging) under human/robot floor operations gated
by venue policy. This actor itself does not dispatch robot/hardware
actions -- it is strictly the operations-coordination layer (event-
record logging, venue-operation scheduling, vendor-order coordination,
safety-concern flagging) any physical-dispatch layer could eventually
feed proposals into, always gated the same way by the independent
EventOperationsGovernor.

## Features

- **Closed proposal-op allowlist**: `log-event-record`,
  `schedule-venue-operation`, `coordinate-vendor-order`,
  `flag-safety-concern` (all `:effect :propose`).
- **Four HARD governor checks** (permanent, un-overridable):
  1. **Venue unverified** -- the target venue's business registration +
     organizer license must exist AND be independently registered/
     verified in the store.
  2. **Vendor unverified** (FLAGSHIP NEW) -- for `:coordinate-vendor-
     order` only, the named event-vendor (catering/AV/security
     staffing) must exist AND be independently registered/verified.
  3. **Effect is :propose** -- any other `:effect` value is rejected.
  4. **Scope exclusion** -- directly finalizing a venue-occupancy-limit
     override, or directly finalizing/issuing a fire-code or emergency-
     egress-compliance clearance, and an op outside the closed
     allowlist are both permanently blocked.
- **Two ESCALATE (SOFT) gates**, either forces human sign-off:
  - `:flag-safety-concern` -- ALWAYS escalates, regardless of confidence
    or phase. A "flag a concern" op is never auto-commit eligible and
    never finalizes a venue-occupancy-limit/fire-code/egress-compliance
    decision itself -- it only surfaces the concern for a human.
  - `:coordinate-vendor-order` above a cost threshold -- a large-value
    procurement proposal always needs a human sign-off.
  - (LLM confidence below the floor also escalates, as with every
    sibling actor.)
- **Staged rollout** (Phase 0→3):
  - Phase 0: read-only
  - Phase 1: event-record logging only (approval-gated)
  - Phase 2: + venue-operation scheduling, vendor-order proposals
    (approval-gated)
  - Phase 3: auto-commits clean, high-confidence, low-cost proposals
    (safety concerns and high-cost vendor orders always escalate)
- **Append-only audit ledger** -- every decision is an immutable log
  entry.
- **langgraph-clj StateGraph** -- one request = one supervised run;
  human-in-the-loop via `interrupt-before`.

### Development

```bash
# Install dependencies (if inside the superproject, use :dev alias for local overrides)
clojure -M:dev -P

# Run tests
clojure -M:test

# Run linter
clojure -M:lint

# Run demo
clojure -M:run
```

### Test suite

- `test/eventops/governor_test.kotoba` -- unit tests of governor hard
  checks, scope exclusion, and the self-trip regression test
- `test/eventops/advisor_test.kotoba` -- advisor proposal shape and
  consistency
- `test/eventops/phase_test.kotoba` -- rollout phase logic
- `test/eventops/governor_contract_test.kotoba` -- full graph integration,
  audit trail
- `test/eventops/store_contract_test.kotoba` -- Store protocol and MemStore
  implementation

### Modules

- `eventops.store` -- SSoT (MemStore, String-keyed venue/vendor
  directories, append-only ledger)
- `eventops.advisor` -- contained intelligence node (mock + real-LLM
  seam)
- `eventops.governor` -- independent compliance layer
- `eventops.phase` -- staged rollout (0→3)
- `eventops.operation` -- langgraph-clj StateGraph
- `eventops.sim` -- demo driver

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`8230`).

## Business-process coverage (honest)

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Booking/exhibitor/attendance data logging (`:log-event-record`) | Real ticketing/badge-scanning-system integration |
| Venue-booking/floor-plan/logistics scheduling coordination (`:schedule-venue-operation`) | Direct facility-management/CAD floor-plan-system integration |
| Event-vendor (catering/AV/security staffing) procurement coordination with a registered, verified vendor, HARD-gated on vendor verification and a double-actuation-free single-proposal shape (`:coordinate-vendor-order`) | Real supplier-ordering-system integration |
| Crowd-safety/venue-occupancy-limit concern flagging, ALWAYS human-gated (`:flag-safety-concern`) | Directly finalizing any venue-occupancy-limit override or fire-code/emergency-egress-compliance clearance -- permanently out of scope, not a gap |
| Immutable audit ledger for every log/schedule/order/flag decision | Post-event financial reconciliation -- a follow-up slice, not in this R0 |

Extending coverage is additive: add the next op (e.g. a badge-reprint-
authorization or an exhibitor-dispute-escalation check) as its own
governed op with its own HARD checks and tests, following the SAME "an
independent governor re-verifies against the actor's own records before
any real-world act" pattern this repo's flagship checks already
establish.

## Maturity

`:implemented` -- `EventOperationsAdvisor` + `EventOperationsGovernor`
run as real, tested code (see `Development` above), following the SAME
governed-actor architecture as every prior actor across this fleet, with
its own distinct, independently-named governor and its own novel
event-vendor-verification check.

## License

Code and implementation templates are AGPL-3.0-or-later.
