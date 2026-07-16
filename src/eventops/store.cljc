(ns eventops.store
  "SSoT for the ISIC-8230 'Organization of conventions and trade shows'
  operations-COORDINATION actor, behind a `Store` protocol so the backend
  is a swap, not a rewrite -- the same seam every `cloud-itonami-isic-*`
  actor in this fleet uses.

  This actor coordinates the back-office operations of a convention/
  trade-show organizer: booking/exhibitor/attendance data logging,
  venue-booking/floor-plan/logistics scheduling, event-vendor (catering,
  AV, security staffing) procurement coordination with registered
  vendors, and safety-concern flagging (crowd/occupancy-limit,
  fire-code, emergency-egress-planning observations). It never itself
  finalizes a venue-occupancy-limit override or a fire-code/emergency-
  egress-compliance clearance -- see `eventops.governor`'s
  `scope-exclusion-violations`, a HARD, permanent, un-overridable block.

  `MemStore` -- atom of EDN. The deterministic default for dev/tests/demo
  (no deps). A `venues` directory keyed by `:venue-id` STRING and a
  `vendors` directory keyed by `:vendor-id` STRING (never keywords --
  consistent keying from the start, avoiding the silent-miss bug that has
  plagued earlier sibling actors).

  A registered/verified venue record (venue business registration +
  organizer license, independently confirmed) must exist before ANY
  proposal targeting that venue may ever commit or escalate --
  `eventops.governor`'s `venue-unverified-violations` re-derives this
  from the venue's own `:registered?`/`:verified?` fields, never from
  proposal self-report. A `:coordinate-vendor-order` proposal
  additionally names a registered event-vendor via its own
  `:vendor-id`; the SAME 'ground truth, not self-report' discipline
  applies via `vendor-unverified-violations`.

  The ledger stays append-only: which venue a proposal targeted, which
  operation, on what basis, committed/held/escalated and approved by whom
  is always a query over an immutable log.")

(defprotocol Store
  (venue-record [s venue-id] "Registered venue record, or nil.
    Venue map: {:venue-id .. :name .. :registered? bool :verified? bool}.
    :registered? -- venue business registration exists.
    :verified? -- organizer license independently confirmed.")
  (all-venue-records [s])
  (vendor-record [s vendor-id] "Registered event-vendor record, or nil.
    Vendor map: {:vendor-id .. :name .. :registered? bool :verified? bool}.")
  (all-vendor-records [s])
  (ledger [s] "the append-only immutable decision-fact log")
  (coordination-log [s] "the append-only committed coordination-proposal history")
  (commit-record! [s record] "apply a committed proposal's record to the SSoT")
  (append-ledger! [s fact] "append one immutable decision fact")
  (with-venue-records [s venues] "replace/seed the venue directory (map venue-id->venue)")
  (with-vendor-records [s vendors] "replace/seed the vendor directory (map vendor-id->vendor)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained venue/vendor directory covering both the
  happy path and the governor's own hard checks, so the actor + tests
  run offline."
  []
  {:venues
   {"venue-1" {:venue-id "venue-1" :name "Riverside Convention Center"
               :registered? true :verified? true}
    "venue-2" {:venue-id "venue-2" :name "Sunset Exhibition Hall"
               :registered? true :verified? true}
    "venue-3" {:venue-id "venue-3" :name "Downtown Pop-Up Trade Show Space (in intake)"
               :registered? true :verified? false}}
   :vendors
   {"vendor-1" {:vendor-id "vendor-1" :name "Northgate AV & Staging Co."
                :registered? true :verified? true}
    "vendor-2" {:vendor-id "vendor-2" :name "Unverified Catering Broker LLC"
                :registered? true :verified? false}}})

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (venue-record [_ venue-id] (get-in @a [:venues venue-id]))
  (all-venue-records [_] (sort-by :venue-id (vals (:venues @a))))
  (vendor-record [_ vendor-id] (get-in @a [:vendors vendor-id]))
  (all-vendor-records [_] (sort-by :vendor-id (vals (:vendors @a))))
  (ledger [_] (:ledger @a))
  (coordination-log [_] (:coordination-log @a))
  (commit-record! [_ record]
    (swap! a update :coordination-log conj record)
    record)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-venue-records [s venues] (when (seq venues) (swap! a assoc :venues venues)) s)
  (with-vendor-records [s vendors] (when (seq vendors) (swap! a assoc :vendors vendors)) s))

(defn seed-db
  "A MemStore seeded with the demo venue/vendor directory. The
  deterministic default."
  []
  (->MemStore (atom (assoc (demo-data) :ledger [] :coordination-log []))))

(defn mem-store
  "A MemStore seeded with explicit `venues`/`vendors` maps (venue-id/
  vendor-id string -> record map) -- the primary test/dev entry point.
  Either may be empty (an unregistered-everywhere venue)."
  ([venues] (mem-store venues {}))
  ([venues vendors]
   (->MemStore (atom {:venues (or venues {}) :vendors (or vendors {})
                       :ledger [] :coordination-log []}))))
