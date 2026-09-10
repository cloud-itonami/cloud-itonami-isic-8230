(ns eventops.advisor
  "EventOperationsAdvisor -- the *contained intelligence node* for the
  ISIC-8230 'Organization of conventions and trade shows' operations-
  coordination actor.

  It drafts exactly four kinds of back-office proposal from a closed
  allowlist: booking/exhibitor/attendance data logging, venue-booking/
  floor-plan/logistics scheduling, event-vendor (catering, AV, security
  staffing) procurement coordination, and crowd-safety/venue-occupancy-
  limit concern flagging. CRITICAL: it is a smart-but-untrusted advisor.
  It returns a *proposal* (with a rationale + the fields it cited),
  never a committed record and NEVER a direct actuation -- every
  proposal's `:effect` is always `:propose`. Every output is censored
  downstream by `eventops.governor` before anything touches the SSoT.

  This advisor NEVER drafts a direct finalization of a
  venue-occupancy-limit override, or a direct finalization/issuance of a
  fire-code-compliance or emergency-egress-compliance clearance -- those
  are permanently out of scope for this actor, not merely
  un-implemented. `eventops.governor`'s `scope-exclusion-violations`
  independently re-scans every proposal for exactly this failure mode (a
  compromised or confused advisor drifting into scope it must never
  touch) and HARD-holds it, regardless of confidence or op.

  Like every sibling actor's advisor, this is a deterministic mock so the
  actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:op         kw             ; echoes the request op
     :venue-id   str
     :summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the scope-exclusion gate
     :cites      [str ..]       ; facts/sources the advisor used -- SCANNED too
     :effect     :propose       ; ALWAYS :propose -- never a direct actuation
     :value      map            ; the draft payload a human/system would review
     :confidence 0..1}")

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

;; ----------------------------- proposal generators -----------------------------

(defn- propose-event-record
  "Draft a booking/exhibitor/attendance data log entry. Pure logging of
  observed event-operations data (exhibitor check-ins, badge counts,
  attendance-headcount deltas) -- never a venue-occupancy-limit
  decision."
  [_db {:keys [venue-id patch]}]
  {:op         :log-event-record
   :venue-id   venue-id
   :summary    (str venue-id " のブッキング/出展者/来場者記録を記録: " (pr-str (keys patch)))
   :rationale  "予約・出展者チェックイン・来場者数の観察記録のみ。会場収容判断は含まない。"
   :cites      [venue-id]
   :effect     :propose
   :value      (merge {:venue-id venue-id} patch)
   :confidence 0.93})

(defn- propose-venue-operation
  "Draft a venue-booking/floor-plan/logistics scheduling proposal (a
  booking/floor-plan calendar entry, never a direct occupancy-limit
  decision)."
  [_db {:keys [venue-id patch]}]
  {:op         :schedule-venue-operation
   :venue-id   venue-id
   :summary    (str venue-id " の会場予約/フロアプラン/搬入出予定を提案: " (pr-str (keys patch)))
   :rationale  "会場予約・展示フロアプラン・搬入出ロジスティクスの調整提案のみ。最終確定は人間が行う。"
   :cites      [venue-id]
   :effect     :propose
   :value      (merge {:venue-id venue-id} patch)
   :confidence 0.88})

(defn- propose-vendor-order
  "Draft an event-vendor (catering, AV, security staffing) procurement
  coordination request naming a registered vendor -- never a finalized
  purchase order; a human always confirms procurement."
  [_db {:keys [venue-id patch]}]
  {:op         :coordinate-vendor-order
   :venue-id   venue-id
   :summary    (str venue-id " 向けケータリング/AV/警備スタッフの発注調整を提案: " (pr-str (keys patch)))
   :rationale  "ケータリング・AV機材・警備スタッフィング等イベントベンダーへの発注調整提案のみ。確定発注は人間が行う。"
   :cites      [venue-id]
   :effect     :propose
   :value      (merge {:venue-id venue-id} patch)
   :confidence 0.90})

(defn- propose-safety-concern
  "Surface an observed crowd-safety/venue-occupancy-limit concern
  (approaching posted capacity, a blocked emergency-egress route, a
  fire-code-adjacent observation) for HUMAN triage. This op ALWAYS
  escalates in `eventops.governor` -- never auto-committed at any phase
  -- regardless of how confident the advisor is that the concern is
  real. Deliberately reports the OBSERVATION only, never a
  finalization/clearance action, so the default rationale never trips
  the governor's `scope-excluded-terms` (see that var's docstring)."
  [_db {:keys [venue-id patch]}]
  {:op         :flag-safety-concern
   :venue-id   venue-id
   :summary    (str venue-id " の安全懸念フラグ: " (pr-str (:concern patch "unknown")))
   :rationale  "会場収容人数上限・消防法適合・避難経路計画に関する懸念の観察事実の報告のみ。常に人間の確認・対応が必要。"
   :cites      [venue-id]
   :effect     :propose
   :value      (merge {:venue-id venue-id} patch)
   :confidence (or (:confidence patch) 0.85)})

;; ----------------------------- default mock advisor -----------------------------

(defn infer
  "Mock advisor: routes to the correct proposal generator."
  [_db {:keys [op out-of-scope?] :as request}]
  (let [proposal (case op
                   :log-event-record (propose-event-record _db request)
                   :schedule-venue-operation (propose-venue-operation _db request)
                   :coordinate-vendor-order (propose-vendor-order _db request)
                   :flag-safety-concern (propose-safety-concern _db request)
                   {})]
    ;; Test hook: allow injecting scope-excluded content to exercise the
    ;; governor's scope-exclusion block end-to-end. Must be cleared before
    ;; production use.
    (if out-of-scope?
      (update proposal :rationale str " -- actually finalized the occupancy-limit override and issued the fire-code clearance")
      proposal)))

(defn trace
  "Audit fact for a proposal generated by this advisor."
  [_request proposal]
  {:t       :advisor-proposal
   :op      (:op proposal)
   :venue-id (:venue-id proposal)
   :summary (:summary proposal)
   :confidence (:confidence proposal)})

(defn mock-advisor
  "The deterministic default advisor for offline demo/test."
  []
  (reify Advisor
    (-advise [_ _store request]
      (infer nil request))))
