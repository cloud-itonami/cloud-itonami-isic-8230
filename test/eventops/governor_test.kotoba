(ns eventops.governor-test
  "Pure unit tests of `eventops.governor/check` against hand-built
  proposals -- the fast, focused complement to `governor-contract-test`'s
  full-graph integration coverage."
  (:require [clojure.test :refer [deftest is testing]]
            [eventops.advisor :as adv]
            [eventops.governor :as gov]
            [eventops.store :as store]))

(def venue-1 {:venue-id "venue-1" :name "Riverside Convention Center" :registered? true :verified? true})
(def venue-3 {:venue-id "venue-3" :name "Downtown Pop-Up Trade Show Space" :registered? true :verified? false})
(def vendor-1 {:vendor-id "vendor-1" :name "Northgate AV & Staging Co." :registered? true :verified? true})
(def vendor-2 {:vendor-id "vendor-2" :name "Unverified Catering Broker LLC" :registered? true :verified? false})

(defn- clean-proposal [op venue-id]
  {:op op :venue-id venue-id :summary "s" :rationale "routine event-operations coordination"
   :cites [venue-id] :effect :propose :value {} :confidence 0.85})

(defn- clean-vendor-order [venue-id vendor-id cost]
  (assoc (clean-proposal :coordinate-vendor-order venue-id)
         :value {:venue-id venue-id :vendor-id vendor-id :estimated-cost cost}))

(deftest venue-unregistered-is-hard
  (testing "no venue record at all -> HARD hold"
    (let [s (store/mem-store {"venue-1" venue-1})
          verdict (gov/check {} nil (clean-proposal :log-event-record "unknown-venue") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:venue-unverified} (map :rule (:violations verdict)))))))

(deftest venue-unverified-is-hard
  (testing "venue registered but not yet verified -> HARD hold"
    (let [s (store/mem-store {"venue-3" venue-3})
          verdict (gov/check {} nil (clean-proposal :log-event-record "venue-3") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:venue-unverified} (map :rule (:violations verdict)))))))

(deftest vendor-missing-on-vendor-order-is-hard
  (testing "vendor-order proposal with no :vendor-id at all -> HARD hold"
    (let [s (store/mem-store {"venue-1" venue-1} {"vendor-1" vendor-1})
          verdict (gov/check {} nil (clean-vendor-order "venue-1" nil 100.0) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:vendor-unverified} (map :rule (:violations verdict)))))))

(deftest vendor-unregistered-on-vendor-order-is-hard
  (testing "vendor-order proposal naming an unknown vendor -> HARD hold"
    (let [s (store/mem-store {"venue-1" venue-1} {"vendor-1" vendor-1})
          verdict (gov/check {} nil (clean-vendor-order "venue-1" "unknown-vendor" 100.0) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:vendor-unverified} (map :rule (:violations verdict)))))))

(deftest vendor-unverified-on-vendor-order-is-hard
  (testing "vendor-order proposal naming a registered-but-unverified vendor -> HARD hold"
    (let [s (store/mem-store {"venue-1" venue-1} {"vendor-1" vendor-1 "vendor-2" vendor-2})
          verdict (gov/check {} nil (clean-vendor-order "venue-1" "vendor-2" 100.0) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:vendor-unverified} (map :rule (:violations verdict)))))))

(deftest vendor-verified-on-vendor-order-is-not-hard-on-vendor-check
  (testing "vendor-order proposal naming a verified vendor never trips :vendor-unverified"
    (let [s (store/mem-store {"venue-1" venue-1} {"vendor-1" vendor-1})
          verdict (gov/check {} nil (clean-vendor-order "venue-1" "vendor-1" 100.0) s)]
      (is (empty? (filter #(= :vendor-unverified (:rule %)) (:violations verdict)))))))

(deftest vendor-check-is-scoped-to-vendor-order-only
  (testing "non-vendor-order ops never trip :vendor-unverified, even with no vendors registered at all"
    (let [s (store/mem-store {"venue-1" venue-1})]
      (doseq [op [:log-event-record :schedule-venue-operation :flag-safety-concern]]
        (let [verdict (gov/check {} nil (clean-proposal op "venue-1") s)]
          (is (empty? (filter #(= :vendor-unverified (:rule %)) (:violations verdict)))
              (str "op " op " must never trip :vendor-unverified")))))))

(deftest effect-not-propose-is-hard
  (testing "any :effect other than :propose is a HARD, un-overridable block"
    (let [s (store/mem-store {"venue-1" venue-1})
          verdict (gov/check {} nil (assoc (clean-proposal :schedule-venue-operation "venue-1") :effect :commit) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:effect-not-propose} (map :rule (:violations verdict)))))))

(deftest op-outside-allowlist-is-hard
  (testing "an op outside the closed four-op allowlist is a scope violation"
    (let [s (store/mem-store {"venue-1" venue-1})
          verdict (gov/check {} nil (clean-proposal :finalize-occupancy-override "venue-1") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:op-not-allowed} (map :rule (:violations verdict)))))))

(deftest occupancy-override-finalization-content-is-hard-and-permanent
  (testing "a proposal whose rationale touches directly finalizing a venue-occupancy-limit override is HARD-blocked regardless of op/confidence"
    (let [s (store/mem-store {"venue-1" venue-1})
          poisoned (assoc (clean-proposal :log-event-record "venue-1")
                          :rationale "finalized the occupancy-limit override for the main hall so the crowd could exceed the posted capacity"
                          :confidence 0.99)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest fire-code-clearance-finalization-content-is-hard
  (testing "a proposal touching issuing a fire-code compliance clearance is HARD-blocked, same as occupancy override"
    (let [s (store/mem-store {"venue-1" venue-1})
          poisoned (assoc (clean-proposal :log-event-record "venue-1")
                          :rationale "issued the fire-code clearance for hall B before the exhibitors arrived"
                          :confidence 0.90)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest egress-clearance-content-is-hard
  (testing "a proposal touching finalizing the emergency-egress clearance is HARD-blocked"
    (let [s (store/mem-store {"venue-1" venue-1})
          poisoned (assoc (clean-proposal :schedule-venue-operation "venue-1")
                          :summary "ops team finalized the emergency-egress clearance ahead of doors-open")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest occupancy-waiver-content-is-hard
  (testing "a proposal touching waiving the occupancy limit is HARD-blocked"
    (let [s (store/mem-store {"venue-1" venue-1} {"vendor-1" vendor-1})
          poisoned (assoc (clean-vendor-order "venue-1" "vendor-1" 100.0)
                          :summary "waived the occupancy limit for the VIP reception in hall A")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest legitimate-safety-concern-is-not-scope-excluded
  (testing "flagging observed occupancy/fire-code/egress concerns as a SAFETY CONCERN (not a clearance finalization) never trips scope-exclusion -- this actor's core valid use case must not be self-blocked"
    (let [s (store/mem-store {"venue-1" venue-1})
          concern (assoc (clean-proposal :flag-safety-concern "venue-1")
                         :value {:concern "hall-b nearing posted occupancy limit during peak exhibitor load-in, one egress route partially obstructed by crates"})
          verdict (gov/check {} nil concern s)]
      (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))
          "raw observation content (occupancy/fire-code/egress concerns) is exactly what this op exists to surface"))))

(deftest safety-concern-always-escalates-clean
  (testing ":flag-safety-concern is always high-stakes/escalate, even when otherwise clean and high confidence"
    (let [s (store/mem-store {"venue-1" venue-1})
          verdict (gov/check {} nil (assoc (clean-proposal :flag-safety-concern "venue-1") :confidence 0.99) s)]
      (is (false? (:hard? verdict)))
      (is (true? (:high-stakes? verdict)))
      (is (true? (:escalate? verdict))))))

(deftest high-cost-vendor-order-always-escalates
  (testing "a :coordinate-vendor-order above the cost threshold is high-stakes/escalate, even when otherwise clean and high confidence"
    (let [s (store/mem-store {"venue-1" venue-1} {"vendor-1" vendor-1})
          expensive (assoc (clean-vendor-order "venue-1" "vendor-1" 5000.0) :confidence 0.97)
          verdict (gov/check {} nil expensive s)]
      (is (false? (:hard? verdict)))
      (is (true? (:high-stakes? verdict)))
      (is (true? (:escalate? verdict))))))

(deftest low-cost-vendor-order-does-not-force-escalate
  (testing "a :coordinate-vendor-order at or below the cost threshold does not trip the high-cost escalate gate"
    (let [s (store/mem-store {"venue-1" venue-1} {"vendor-1" vendor-1})
          cheap (assoc (clean-vendor-order "venue-1" "vendor-1" 420.0) :confidence 0.9)
          verdict (gov/check {} nil cheap s)]
      (is (false? (:hard? verdict)))
      (is (false? (:high-stakes? verdict)))
      (is (false? (:escalate? verdict))))))

;; ----------------------------- self-trip regression -----------------------------
;;
;; A known bug class in this actor fleet: the governor's own
;; scope-exclusion term list is sometimes phrased as a bare noun (e.g.
;; "occupancy limit" or "fire code"), which then accidentally matches
;; inside the mock advisor's own DEFAULT rationale/disclaimer text for a
;; legitimate, allowed proposal -- causing the actor to self-block its
;; own happy path. This is a dedicated regression test: every op the
;; default mock advisor can generate, with default (non-`out-of-scope?`)
;; request patches, must NEVER trip `:scope-excluded` or
;; `:op-not-allowed`.
(deftest default-mock-advisor-proposals-never-self-trip-scope-exclusion
  (testing "the default mock advisor's own proposals for every allowed op never trip the governor's scope-exclusion check"
    (let [s (store/mem-store {"venue-1" venue-1} {"vendor-1" vendor-1})]
      (doseq [op [:log-event-record :schedule-venue-operation :coordinate-vendor-order
                  :flag-safety-concern]]
        (let [patch (if (= op :coordinate-vendor-order)
                      {:item "AV staging package" :estimated-cost 420.0 :vendor-id "vendor-1"}
                      {})
              proposal (adv/infer nil {:op op :venue-id "venue-1" :patch patch})
              verdict (gov/check {:venue-id "venue-1"} nil proposal s)]
          (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))
              (str "default advisor proposal for " op " must never self-trip :scope-excluded -- rationale/summary: "
                   (pr-str (select-keys proposal [:summary :rationale]))))
          (is (empty? (filter #(= :op-not-allowed (:rule %)) (:violations verdict)))
              (str "default advisor proposal for " op " must always be inside the closed op allowlist")))))))
