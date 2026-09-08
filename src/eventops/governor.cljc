(ns eventops.governor
  "EventOperationsGovernor -- the independent compliance layer that earns
  the EventOperationsAdvisor the right to commit. The advisor has no
  notion of whether a venue is actually registered and license-verified,
  whether a named event-vendor procurement counterparty is itself a
  registered/verified vendor, whether its own proposed `:effect`
  secretly claims a direct actuation instead of a mere proposal, or
  whether it has silently drifted into a permanently out-of-scope
  decision area, so this MUST be a separate system able to *reject* a
  proposal and fall back to HOLD.

  This actor's scope is deliberately narrow -- COORDINATION ONLY
  (booking/exhibitor/attendance data logging, venue-booking/floor-plan/
  logistics scheduling, event-vendor -- catering/AV/security-staffing --
  procurement coordination, crowd-safety/venue-occupancy-limit concern
  flagging). It NEVER performs or authorizes:
    - directly finalizing a venue-occupancy-limit override (letting a
      crowd exceed the posted/fire-code-derived capacity)
    - directly finalizing or issuing a fire-code-compliance clearance
    - directly finalizing or issuing an emergency-egress-compliance
      clearance/sign-off

  Four HARD checks, ALL permanent, un-overridable by any human approval:

    1. Venue unverified            -- the target venue record must exist
                                       AND be independently confirmed
                                       `:registered?`/`:verified?`
                                       (venue business registration +
                                       organizer license) in the store
                                       before ANY proposal for it may
                                       commit or even escalate. Never
                                       trusts a proposal's own claim
                                       about the venue -- re-derived
                                       from the venue's own record, the
                                       same 'ground truth, not
                                       self-report' discipline every
                                       sibling actor's governor uses.
    2. Vendor unverified           -- for `:coordinate-vendor-order`
                                       ONLY, the proposal's own drafted
                                       `:value` must name a `:vendor-id`
                                       that resolves to an independently
                                       `:registered?`/`:verified?`
                                       event-vendor (catering/AV/
                                       security-staffing) record. A
                                       missing vendor-id, or one that
                                       resolves to an unregistered or
                                       unverified vendor, is a HARD
                                       block.
    3. Effect not :propose         -- every proposal's `:effect` MUST be
                                       `:propose`. Any other effect value
                                       is, by construction, a claim to
                                       directly actuate/commit outside
                                       governance -- HARD block, not
                                       merely low-confidence.
    4. Scope exclusion             -- ANY proposal (regardless of op)
                                       whose op, summary, rationale,
                                       cites or draft value touches
                                       directly finalizing a
                                       venue-occupancy-limit override, or
                                       directly finalizing/issuing a
                                       fire-code-compliance or
                                       emergency-egress-compliance
                                       clearance, is a HARD, PERMANENT
                                       block -- this actor's charter
                                       excludes that territory
                                       structurally, not as a rollout
                                       milestone. Evaluated
                                       UNCONDITIONALLY on every
                                       proposal. An op outside the
                                       closed four-op allowlist is the
                                       SAME failure mode (an advisor
                                       proposing something it was never
                                       authorized to propose) and is
                                       folded into this same check.
                                       `:flag-safety-concern` itself is
                                       never excluded by this check --
                                       surfacing a crowd-safety/
                                       occupancy-limit/fire-code/egress-
                                       planning concern for a human is
                                       exactly this actor's job; only
                                       FINALIZING/issuing/clearing that
                                       concern is excluded (see
                                       `scope-excluded-terms` below --
                                       phrased as the finalization/
                                       execution ACTION, never a bare
                                       noun like 'occupancy limit' or
                                       'fire code' or 'egress', so the
                                       default mock advisor's own
                                       `:flag-safety-concern` rationale
                                       never self-trips this check).

  Two ESCALATE (SOFT) gates, either forces human sign-off:
    - LLM confidence below the floor.
    - The op is `:flag-safety-concern` -- ALWAYS escalates to a human,
      regardless of confidence, regardless of how clean the proposal
      otherwise is. `eventops.phase` independently agrees:
      `:flag-safety-concern` is never a member of any phase's `:auto`
      set either -- two layers, not one.
    - A `:coordinate-vendor-order` whose drafted `:value` names an
      `:estimated-cost` above `vendor-cost-threshold` -- a large-value
      event-vendor procurement proposal always needs a human sign-off,
      even when the governor and phase would otherwise allow
      auto-commit."
  (:require [kotoba.lang.text :as str]
            [eventops.store :as store]))

(def confidence-floor 0.6)

(def vendor-cost-threshold
  "Example single-event vendor (catering/AV/security-staffing)
  procurement threshold (USD-equivalent units, domain-illustrative --
  not a universal cross-domain constant). A `:coordinate-vendor-order`
  proposal citing an `:estimated-cost` above this value ALWAYS escalates
  to human sign-off, regardless of confidence or rollout phase."
  1000.0)

(def allowed-ops
  "The closed proposal-op allowlist -- an op outside this set is a scope
  violation by construction (see `scope-exclusion-violations`)."
  #{:log-event-record :schedule-venue-operation
    :coordinate-vendor-order :flag-safety-concern})

(def always-escalate-ops
  "Ops that ALWAYS require human sign-off, clean or not."
  #{:flag-safety-concern})

(def scope-excluded-terms
  "Case-insensitive substrings that mark a proposal as touching a
  permanently out-of-scope decision area -- directly finalizing a
  venue-occupancy-limit override, or directly finalizing/issuing a
  fire-code-compliance or emergency-egress-compliance clearance, rather
  than merely flagging the concern for a human. Scanned across the
  proposal's op/summary/rationale/cites/value, never trusting the
  advisor's own framing of its intent.

  CRITICAL: every term here is phrased as the finalization/execution
  ACTION (e.g. 'finalize the occupancy-limit override', 'issue the
  fire-code clearance'), never a bare noun like 'occupancy limit',
  'fire code', 'egress' or 'crowd safety' -- a bare noun would
  accidentally match inside this actor's own legitimate
  `:flag-safety-concern` default proposal text (whose whole job is to
  talk about occupancy-limit/fire-code/egress-planning concerns) and
  self-block the happy path. See
  `eventops.governor-test/default-mock-advisor-proposals-never-self-trip-scope-exclusion`
  for the regression test."
  ["finalize the occupancy override" "finalized the occupancy override" "finalizes the occupancy override"
   "finalize the occupancy-limit override" "finalized the occupancy-limit override" "finalizing the occupancy-limit override"
   "override the posted occupancy limit" "overrode the posted occupancy limit" "overriding the posted occupancy limit"
   "waive the occupancy limit" "waived the occupancy limit" "waiving the occupancy limit"
   "approve the occupancy-limit override" "approved the occupancy-limit override" "approving the occupancy-limit override"
   "authorize the occupancy override" "authorized the occupancy override" "authorizing the occupancy override"
   "let the crowd exceed the posted capacity" "let the crowd exceed capacity"
   "grant the fire-code clearance" "granted the fire-code clearance" "granting the fire-code clearance"
   "issue the fire-code clearance" "issued the fire-code clearance" "issuing the fire-code clearance"
   "issue the fire-code compliance clearance" "issued the fire-code compliance clearance" "issuing the fire-code compliance clearance"
   "certify fire-code compliance" "certified fire-code compliance" "certifying fire-code compliance"
   "finalize fire-code compliance" "finalized fire-code compliance" "finalizing fire-code compliance"
   "clear the emergency-egress compliance" "cleared the emergency-egress compliance" "clearing the emergency-egress compliance"
   "sign off on the egress-compliance clearance" "signed off on the egress-compliance clearance" "signing off on the egress-compliance clearance"
   "finalize the emergency-egress clearance" "finalized the emergency-egress clearance" "finalizing the emergency-egress clearance"
   "issue the emergency-egress clearance" "issued the emergency-egress clearance" "issuing the emergency-egress clearance"
   "収容人数上限の解除を確定" "収容人数上限の解除を実行した" "占有制限緩和を承認した"
   "消防法適合証明を発行した" "消防法適合を確定した" "避難計画の適合認定を確定"
   "避難経路の適合クリアランスを発行" "避難適合証明を発行した"])

;; ----------------------------- checks -----------------------------

(defn- venue-unverified-violations
  "The target venue must exist AND be independently
  `:registered?`/`:verified?` in the store -- never trust the proposal's
  own `:venue-id` claim without a store lookup."
  [{:keys [venue-id]} st]
  (let [v (store/venue-record st venue-id)]
    (when-not (and v (:registered? v) (:verified? v))
      [{:rule :venue-unverified
        :detail (str venue-id " は未登録または未検証の会場 -- いかなる提案も進められない")}])))

(defn- vendor-unverified-violations
  "For `:coordinate-vendor-order` ONLY, the proposal's own drafted
  `:value` must name a `:vendor-id` that resolves to an independently
  `:registered?`/`:verified?` event-vendor record. A missing vendor-id,
  or one that resolves to an unregistered/unverified vendor, is a HARD
  block -- never trust the proposal's own vendor claim without a store
  lookup, the SAME 'ground truth, not self-report' discipline as
  `venue-unverified-violations`, reapplied to the event-vendor
  counterparty."
  [proposal st]
  (when (= :coordinate-vendor-order (:op proposal))
    (let [vendor-id (get-in proposal [:value :vendor-id])
          v (and vendor-id (store/vendor-record st vendor-id))]
      (when-not (and v (:registered? v) (:verified? v))
        [{:rule :vendor-unverified
          :detail (str (or vendor-id "(vendor-id missing)")
                        " は未登録または未検証のイベントベンダー -- 発注調整提案を進められない")}]))))

(defn- effect-not-propose-violations
  "`:effect` must ALWAYS be `:propose` -- any other value is a claim to
  directly actuate/commit outside governance."
  [proposal]
  (when (not= :propose (:effect proposal))
    [{:rule :effect-not-propose
      :detail (str ":effect は :propose のみ許可されるが " (pr-str (:effect proposal)) " が提案された")}]))

(defn- text-blob
  "Flatten every advisor-authored field on a proposal into one lower-cased
  blob the scope-exclusion scan checks."
  [proposal]
  (str/lower (pr-str (select-keys proposal [:op :summary :rationale :cites :value]))))

(defn- scope-exclusion-violations
  "HARD, PERMANENT block: a proposal outside the closed op allowlist, or
  one whose content touches directly finalizing a venue-occupancy-limit
  override or directly finalizing/issuing a fire-code/emergency-egress-
  compliance clearance, regardless of confidence or how clean every
  other check is. Evaluated UNCONDITIONALLY on every proposal."
  [proposal]
  (let [op (:op proposal)
        blob (text-blob proposal)]
    (cond
      (not (contains? allowed-ops op))
      [{:rule :op-not-allowed
        :detail (str (pr-str op) " は許可された操作(closed allowlist)に含まれない")}]

      (some #(str/includes? blob %) scope-excluded-terms)
      [{:rule :scope-excluded
        :detail "会場収容人数上限の解除確定・消防法/避難経路適合クリアランスの発行確定など安全確定行為(venue-safety-clearance finalization)に触れる提案は永久に禁止"}])))

(defn- high-cost-vendor-order?
  "A `:coordinate-vendor-order` proposal citing an `:estimated-cost`
  above `vendor-cost-threshold` -- always needs human sign-off (SOFT
  escalate, not a hard block: the order itself is in scope, only its
  size requires a human)."
  [proposal]
  (and (= :coordinate-vendor-order (:op proposal))
       (some-> proposal :value :estimated-cost (> vendor-cost-threshold))))

(defn check
  "Censors an EventOperationsAdvisor proposal against the governor
  rules. Returns {:ok? bool :violations [..] :confidence c :escalate?
  bool :high-stakes? bool :hard? bool}."
  [request _context proposal store]
  (let [venue-id (or (:venue-id proposal) (:venue-id request))
        hard (into []
                   (concat (venue-unverified-violations {:venue-id venue-id} store)
                           (vendor-unverified-violations proposal store)
                           (effect-not-propose-violations proposal)
                           (scope-exclusion-violations proposal)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (or (always-escalate-ops (:op proposal))
                              (high-cost-vendor-order? proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :venue-id   (:venue-id request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
