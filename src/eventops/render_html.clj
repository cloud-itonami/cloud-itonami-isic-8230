(ns eventops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave5 rollout ledger seq 6): this repo previously had NO demo page and
  no generator at all. This namespace drives the REAL actor stack
  (`eventops.operation` -> `eventops.governor` -> `eventops.store`)
  through a scenario built from the actor's OWN seeded demo data
  (`eventops.store/seed-db`, venues venue-1/venue-2/venue-3, vendors
  vendor-1/vendor-2) and renders the result deterministically -- no
  invented numbers, no timestamps in the page content, byte-identical
  across reruns against the same seed (verified by diffing two
  consecutive runs).

  NOTE for future porters of this template: this repo's OWN
  `eventops.sim` demo driver (`clojure -M:dev:run`) was checked BEFORE
  writing this file and confirmed clean -- every id it drives
  (venue-1/venue-2/venue-3/venue-99, vendor-1/vendor-2) matches
  `eventops.store/demo-data` exactly, and every governor-hold basis
  (`:venue-unverified`/`:vendor-unverified`/`:effect-not-propose`/
  `:scope-excluded`) in its printed ledger is a genuine violation this
  file also exercises below. Unlike `cloud-itonami-isic-851`'s
  `schoolops.sim` (which shipped with copy-pasted eldercare ids that
  never matched its own seeded students), this repo has no such bug.
  This renderer was written independently of `eventops.sim` and keeps
  its own `run-demo!` scenario below so this build-time generator has no
  runtime dependency on the demo driver either way -- every field read
  by `render` below is real governor/store output, not a hand-typed
  copy.

  Also confirmed: `eventops.operation`'s `:commit` node genuinely
  mutates the store (`store/commit-record!` + `store/append-ledger!`,
  both real `swap!` calls on `eventops.store/MemStore`) -- it does NOT
  exhibit the isic-4322-class silent-no-op bug (a commit node that
  claims to write but never calls the real store-mutation function).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [eventops.store :as store]
            [eventops.operation :as op]
            [langgraph.graph :as g]))

(def ^:private operator
  {:actor-id "op-1" :actor-role :event-operations-coordinator :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach, ordered so each venue's LAST ledger fact tells a
  clean story: venue-1 clears a booking/exhibitor data-log entry, a
  venue-booking/floor-plan scheduling proposal and a low-cost verified-
  vendor coordination (all three auto-commit clean at phase 3, no
  capital risk); a vendor-order naming the unverified `vendor-2` is
  attempted first and HARD-holds on `:vendor-unverified` (never reaches
  a human) BEFORE the clean vendor order, so it doesn't clobber venue-1's
  final status; venue-1's high-cost vendor order (>$1,000) and its
  safety-concern flag both ALWAYS escalate (per `always-escalate-ops`
  and `vendor-cost-threshold`) and are approved by a human last, so
  venue-1's final ledger entry reads 'committed'. venue-3 (registered
  but NOT `:verified?` in the seed data) HARD-holds its own attempt on
  `:venue-unverified`. venue-2 (registered AND verified) is otherwise
  idle, so it is used to exercise a proposal that has drifted into the
  permanently-excluded venue-occupancy-override/fire-code/egress-
  clearance-finalization scope -- HARD-holds on `:scope-excluded` even
  though the venue itself is clean, proving the scope check is
  unconditional. Every HARD hold never reaches a human. Returns the
  resulting store -- every field read by `render` below is real
  governor/store output, not a hand-typed copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)]
    (exec! actor "v1-intake" {:op :log-event-record :venue-id "venue-1"
                               :patch {:exhibitor-checkins 42 :badges-issued 310 :attendance-delta 12}})

    (exec! actor "v1-vendor-unverified" {:op :coordinate-vendor-order :venue-id "venue-1"
                                          :patch {:item "catering for opening reception" :quantity 1
                                                   :estimated-cost 300.0 :vendor-id "vendor-2"}})

    (exec! actor "v1-schedule" {:op :schedule-venue-operation :venue-id "venue-1"
                                 :patch {:hall "hall-b" :date "2026-08-20" :window "06:00-10:00"
                                          :activity "exhibitor-load-in"}})

    (exec! actor "v1-vendor-low" {:op :coordinate-vendor-order :venue-id "venue-1"
                                   :patch {:item "AV staging package" :quantity 1 :estimated-cost 420.0
                                            :vendor-id "vendor-1"}})

    (exec! actor "v1-vendor-high" {:op :coordinate-vendor-order :venue-id "venue-1"
                                    :patch {:item "main-stage AV + rigging package" :quantity 1
                                             :estimated-cost 3200.0 :vendor-id "vendor-1"}})
    (approve! actor "v1-vendor-high")

    (exec! actor "v1-safety" {:op :flag-safety-concern :venue-id "venue-1"
                               :patch {:concern "hall-b nearing posted occupancy limit during peak exhibitor load-in, one egress route partially obstructed by crates"
                                        :confidence 0.92}})
    (approve! actor "v1-safety")

    (exec! actor "v3-intake" {:op :log-event-record :venue-id "venue-3" :patch {:exhibitor-checkins 10}})

    (exec! actor "v2-scope" {:op :log-event-record :venue-id "venue-2" :out-of-scope? true :patch {}})
    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger venue-id]
  (last (filter #(= (:venue-id %) venue-id) ledger)))

(defn- status-cell [ledger venue-id]
  (let [f (last-fact-for ledger venue-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (-> f :violations first :rule)]
        (case rule
          :venue-unverified "<span class=\"critical\">HARD hold &middot; unverified venue</span>"
          :vendor-unverified "<span class=\"critical\">HARD hold &middot; unverified event-vendor</span>"
          :effect-not-propose "<span class=\"critical\">HARD hold &middot; effect not :propose</span>"
          :scope-excluded "<span class=\"critical\">HARD hold &middot; scope-excluded</span>"
          (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>")))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- venue-row [ledger {:keys [venue-id name registered? verified?]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc venue-id) (esc name)
          (if (and registered? verified?)
            "<span class=\"ok\">registered &amp; verified</span>"
            "<span class=\"warn\">registered, unverified</span>")
          (status-cell ledger venue-id)))

(defn- vendor-row [{:keys [vendor-id name registered? verified?]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc vendor-id) (esc name)
          (if (and registered? verified?)
            "<span class=\"ok\">registered &amp; verified</span>"
            "<span class=\"warn\">registered, unverified</span>")))

(defn- ledger-row [{:keys [t op venue-id disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc venue-id)
          (esc (or (some->> basis (map name) (str/join ", ")) (some-> disposition name) ""))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own closed op contract (README
  ;; `Ops` table, `eventops.governor`/`eventops.phase`) -- documentation
  ;; of fixed behavior, not runtime telemetry, so it is legitimately
  ;; hand-described rather than derived from a live run.
  ["        <tr><td><code>:log-event-record</code></td><td><span class=\"ok\">phase-3 auto when clean &middot; venue must be registered &amp; verified</span></td></tr>"
   "        <tr><td><code>:schedule-venue-operation</code></td><td><span class=\"ok\">phase-3 auto when clean &middot; venue must be registered &amp; verified</span></td></tr>"
   "        <tr><td><code>:coordinate-vendor-order</code></td><td><span class=\"ok\">phase-3 auto when clean, under $1,000 &amp; vendor registered/verified</span> &middot; <span class=\"warn\">ALWAYS human approval above $1,000</span></td></tr>"
   "        <tr><td><code>:flag-safety-concern</code></td><td><span class=\"warn\">ALWAYS human approval &middot; never auto, any phase</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        venues (store/all-venue-records db)
        vendors (store/all-vendor-records db)
        venue-rows (str/join "\n" (map (partial venue-row ledger) venues))
        vendor-rows (str/join "\n" (map vendor-row vendors))
        ledger-rows (str/join "\n" (map ledger-row ledger))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-8230 &middot; convention/trade-show operations coordination</title><style>\n"
     "table { width: 100%; border-collapse: collapse; font-size: 14px; }\n"
     ".ok { color: #137a3f; }\n"
     "body { font-family: system-ui,-apple-system,sans-serif; margin: 0; color: #1a1a1a; background: #fafafa; }\n"
     "header.bar { display: flex; align-items: center; gap: 12px; padding: 12px 20px; background: #fff; border-bottom: 1px solid #e5e5e5; }\n"
     "th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #f0f0f0; }\n"
     "h2 { margin-top: 0; font-size: 15px; }\n"
     ".warn { color: #b25c00; background: #fff8e1; padding: 2px 6px; border-radius: 4px; }\n"
     "main { max-width: 980px; margin: 24px auto; padding: 0 20px; }\n"
     "header.bar h1 { font-size: 18px; margin: 0; font-weight: 600; }\n"
     ".muted { color: #888; font-size: 13px; }\n"
     ".critical { color: #fff; background: #b3261e; padding: 2px 6px; border-radius: 4px; font-weight: 600; }\n"
     ".card { background: #fff; border: 1px solid #e5e5e5; border-radius: 8px; padding: 16px; margin-bottom: 16px; }\n"
     ".err { color: #b3261e; background: #fbe9e7; padding: 2px 6px; border-radius: 4px; }\n"
     "th { font-weight: 600; color: #555; font-size: 12px; text-transform: uppercase; letter-spacing: 0.04em; }\n"
     "header.bar .badge { margin-left: auto; font-size: 12px; color: #666; }\n"
     "code { font-size: 12px; background: #f4f4f4; padding: 1px 4px; border-radius: 3px; }\n"
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Organization of conventions and trade shows (ISIC 8230) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · venue-safety-clearance finalization always out of scope</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Venues</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>eventops.store</code> via <code>eventops.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Venue</th><th>Name</th><th>Registration status</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     venue-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Registered event-vendors</h2>\n"
     "    <p class=\"muted\">Catering / AV / security-staffing counterparties — <code>:coordinate-vendor-order</code> HARD-holds unless the named vendor resolves to a registered &amp; verified record here.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Vendor</th><th>Name</th><th>Registration status</th></tr></thead>\n"
     "      <tbody>\n"
     vendor-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (EventOperationsGovernor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden. Venue and event-vendor identity are independently re-verified against the store, never trusted from the proposal; directly finalizing a venue-occupancy-limit override or a fire-code/emergency-egress-compliance clearance is permanently out of scope.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Venue</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/coordination-log db)) "committed coordination records )")))
