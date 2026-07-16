(ns eventops.store-contract-test
  "Contract tests for `eventops.store/Store` protocol."
  (:require [clojure.test :refer [deftest is testing]]
            [eventops.store :as store]))

(deftest mem-store-venue-lookup
  (testing "MemStore can store and retrieve venues by ID (string keys)"
    (let [venues {"v1" {:venue-id "v1" :name "Alice's Convention Hall" :registered? true :verified? true}}
          s (store/mem-store venues)]
      (is (some? (store/venue-record s "v1")))
      (is (nil? (store/venue-record s "v99"))))))

(deftest mem-store-all-venue-records
  (testing "MemStore returns all venues in sorted order"
    (let [venues {"v2" {:venue-id "v2" :name "Bob's Exhibition Hall"}
                  "v1" {:venue-id "v1" :name "Alice's Convention Hall"}
                  "v3" {:venue-id "v3" :name "Carol's Trade Show Center"}}
          s (store/mem-store venues)
          all-v (store/all-venue-records s)]
      (is (= 3 (count all-v)))
      (is (= "v1" (:venue-id (first all-v))))
      (is (= "v3" (:venue-id (last all-v)))))))

(deftest mem-store-vendor-lookup
  (testing "MemStore can store and retrieve vendors by ID (string keys)"
    (let [vendors {"v1" {:vendor-id "v1" :name "Acme AV Supply" :registered? true :verified? true}}
          s (store/mem-store {} vendors)]
      (is (some? (store/vendor-record s "v1")))
      (is (nil? (store/vendor-record s "v99"))))))

(deftest mem-store-all-vendor-records
  (testing "MemStore returns all vendors in sorted order"
    (let [vendors {"v2" {:vendor-id "v2" :name "Beta Catering"}
                   "v1" {:vendor-id "v1" :name "Acme AV Supply"}}
          s (store/mem-store {} vendors)
          all-v (store/all-vendor-records s)]
      (is (= 2 (count all-v)))
      (is (= "v1" (:vendor-id (first all-v)))))))

(deftest mem-store-ledger-append
  (testing "MemStore append-ledger! adds facts to immutable log"
    (let [s (store/mem-store {})
          fact1 {:t :test :data "fact1"}
          fact2 {:t :test :data "fact2"}]
      (is (= 0 (count (store/ledger s))))
      (store/append-ledger! s fact1)
      (is (= 1 (count (store/ledger s))))
      (store/append-ledger! s fact2)
      (is (= 2 (count (store/ledger s)))))))

(deftest mem-store-coordination-log
  (testing "MemStore commit-record! appends to coordination-log"
    (let [s (store/mem-store {})
          record {:op :log-event-record :venue-id "v1" :value {:exhibitor-checkins 42}}]
      (is (= 0 (count (store/coordination-log s))))
      (store/commit-record! s record)
      (is (= 1 (count (store/coordination-log s))))
      (is (= record (first (store/coordination-log s)))))))

(deftest mem-store-with-venue-records
  (testing "MemStore with-venue-records replaces the venue directory"
    (let [s (store/mem-store {})
          new-venues {"v1" {:venue-id "v1" :name "Alice's Convention Hall"}}]
      (is (= 0 (count (store/all-venue-records s))))
      (store/with-venue-records s new-venues)
      (is (= 1 (count (store/all-venue-records s)))))))

(deftest mem-store-with-vendor-records
  (testing "MemStore with-vendor-records replaces the vendor directory"
    (let [s (store/mem-store {})
          new-vendors {"v1" {:vendor-id "v1" :name "Acme AV Supply"}}]
      (is (= 0 (count (store/all-vendor-records s))))
      (store/with-vendor-records s new-vendors)
      (is (= 1 (count (store/all-vendor-records s)))))))

(deftest seed-db-has-demo-data
  (testing "seed-db creates a populated MemStore with demo venues and vendors"
    (let [s (store/seed-db)]
      (is (> (count (store/all-venue-records s)) 0))
      (is (some? (store/venue-record s "venue-1")))
      (is (some? (store/venue-record s "venue-2")))
      (is (some? (store/venue-record s "venue-3")))
      (is (> (count (store/all-vendor-records s)) 0))
      (is (some? (store/vendor-record s "vendor-1")))
      (is (some? (store/vendor-record s "vendor-2"))))))

(deftest demo-data-string-key-consistency
  (testing "demo-data uses string keys, not keywords, for venue-id/vendor-id"
    (let [demo (store/demo-data)
          venues (:venues demo)
          vendors (:vendors demo)]
      (doseq [[k v] venues]
        (is (string? k) "venue keys must be strings")
        (is (string? (:venue-id v)) "venue-id must be string")
        (is (= k (:venue-id v)) "key must match venue-id"))
      (doseq [[k v] vendors]
        (is (string? k) "vendor keys must be strings")
        (is (string? (:vendor-id v)) "vendor-id must be string")
        (is (= k (:vendor-id v)) "key must match vendor-id")))))

(deftest store-is-append-only
  (testing "appended facts are immutable and never removed"
    (let [s (store/seed-db)
          fact1 {:t :event1 :data "a"}
          fact2 {:t :event2 :data "b"}]
      (store/append-ledger! s fact1)
      (let [ledger-after-1 (store/ledger s)]
        (store/append-ledger! s fact2)
        (let [ledger-after-2 (store/ledger s)]
          (is (= (count ledger-after-1) (dec (count ledger-after-2))))
          (is (every? #(some (fn [x] (= x %)) ledger-after-2) ledger-after-1)
              "all prior facts must still be present"))))))
