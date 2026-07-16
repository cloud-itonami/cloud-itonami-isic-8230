# Contributing

`cloud-itonami-isic-8230` accepts contributions to the OSS blueprint,
capability bindings, policy tests, documentation and operator model.

## Development

```bash
clojure -M:test
clojure -M:lint
```

## Rules
- Do not commit real attendee, exhibitor, vendor or safety-incident
  data.
- Keep event-record logging, venue-operation scheduling, vendor-order
  coordination and safety-concern flagging behind the
  EventOperationsGovernor.
- Treat convention/trade-show operations workflows as high-risk: add
  tests for venue/vendor verification, effect discipline, scope
  exclusion, escalation and audit logging.
- Never let the actor's proposal-op allowlist include an op that
  directly finalizes a venue-occupancy-limit override or a fire-code/
  emergency-egress-compliance clearance -- that decision area is
  permanently out of scope, structurally, not a rollout milestone.
- Never phrase a governor scope-exclusion term as a bare noun (e.g.
  "occupancy limit", "fire code", "egress") -- phrase it as the
  finalization/execution ACTION (e.g. "finalize the occupancy-limit
  override", "issue the fire-code clearance"), and add/extend the
  `default-mock-advisor-proposals-never-self-trip-scope-exclusion`
  regression test for any new term. A bare-noun term will self-trip this
  actor's own legitimate `:flag-safety-concern` happy path -- see
  `eventops.governor/scope-excluded-terms`'s docstring.
- Any new "flag a concern" op must always escalate to a human and must
  never be added to any phase's `:auto` set.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests
PRs should describe: what behavior changed, which policy invariant is
affected, how it was tested, whether operator or certification docs need
updates.
