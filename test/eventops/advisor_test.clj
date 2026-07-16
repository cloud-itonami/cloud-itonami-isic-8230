(ns eventops.advisor-test
  "Unit tests of `eventops.advisor` proposal generation."
  (:require [clojure.test :refer [deftest is testing]]
            [eventops.advisor :as adv]
            [eventops.store :as store]))

(def db (store/seed-db))

(deftest propose-event-record-shape
  (testing "event-record proposal has correct shape and fields"
    (let [p (adv/infer db {:op :log-event-record
                           :venue-id "venue-1"
                           :patch {:exhibitor-checkins 42 :badges-issued 310 :attendance-delta 12}})]
      (is (= :log-event-record (:op p)))
      (is (= "venue-1" (:venue-id p)))
      (is (= :propose (:effect p)))
      (is (<= 0 (:confidence p) 1))
      (is (map? (:value p)))
      (is (contains? (:value p) :venue-id)))))

(deftest propose-venue-operation-shape
  (testing "venue-operation proposal has correct shape"
    (let [p (adv/infer db {:op :schedule-venue-operation
                           :venue-id "venue-2"
                           :patch {:hall "hall-a" :date "2026-08-20"}})]
      (is (= :schedule-venue-operation (:op p)))
      (is (= "venue-2" (:venue-id p)))
      (is (= :propose (:effect p))))))

(deftest propose-vendor-order-shape
  (testing "vendor-order proposal has correct shape"
    (let [p (adv/infer db {:op :coordinate-vendor-order
                           :venue-id "venue-1"
                           :patch {:item "AV staging package" :quantity 1 :estimated-cost 420.0
                                   :vendor-id "vendor-1"}})]
      (is (= :coordinate-vendor-order (:op p)))
      (is (= :propose (:effect p)))
      (is (string? (:summary p)))
      (is (= "vendor-1" (get-in p [:value :vendor-id]))))))

(deftest propose-safety-concern-shape
  (testing "safety-concern proposal always escalates"
    (let [p (adv/infer db {:op :flag-safety-concern
                           :venue-id "venue-1"
                           :patch {:concern "occupancy nearing limit in hall B"}})]
      (is (= :flag-safety-concern (:op p)))
      (is (= :propose (:effect p)))
      (is (string? (:summary p))))))

(deftest all-proposals-effect-is-always-propose
  (testing "every proposal type has :effect :propose, never direct actuation"
    (doseq [op [:log-event-record :schedule-venue-operation :coordinate-vendor-order
                :flag-safety-concern]]
      (let [p (adv/infer db {:op op :venue-id "venue-1" :patch {}})]
        (is (= :propose (:effect p))
            (str "op " op " must have :effect :propose"))))))

(deftest rationale-string-is-present
  (testing "every proposal has a rationale explaining the advisor's thinking"
    (doseq [op [:log-event-record :schedule-venue-operation :coordinate-vendor-order
                :flag-safety-concern]]
      (let [p (adv/infer db {:op op :venue-id "venue-1" :patch {}})]
        (is (string? (:rationale p))
            (str "op " op " must have a :rationale string"))))))
