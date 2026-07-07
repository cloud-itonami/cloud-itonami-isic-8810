(ns care.store
  "SSoT for the care actor, behind a `Store` protocol so the backend is
  a swap, not a rewrite -- the same seam every prior `cloud-itonami-
  isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/care/store_contract_test.clj), which is the whole point: the
  actor, the Safeguarding Governor and the audit ledger never know
  which SSoT they run on.

  Like every prior dual-actuation sibling, this actor has TWO
  actuation events (dispatching a check-in, closing a case) acting on
  the SAME entity (a `case`), each with its OWN history collection,
  sequence counter and dedicated double-actuation-guard boolean
  (`:checkin-dispatched?`/`:case-closed?`, never a `:status` value) --
  the same discipline every prior sibling governor's guards establish,
  informed by `cloud-itonami-isic-6492`'s status-lifecycle bug
  (ADR-2607071320).

  NOTE on naming: the protocol's per-entity accessor is `case-of`, not
  `case` -- `case` is a Clojure special form and every backend's
  `commit-record!` dispatches on `effect` via `(case effect ...)`, so
  the entity accessor must not shadow it.

  The ledger stays append-only on every backend: 'which case was
  screened for an unresolved safeguarding signal, which check-in was
  dispatched, which case was closed, on what jurisdictional basis,
  approved by whom' is always a query over an immutable log -- the
  audit trail a family trusting a care-coordination operator needs,
  and the evidence an operator needs if a dispatch or closure decision
  is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [care.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (case-of [s id])
  (all-cases [s])
  (safeguarding-screen-of [s case-id] "committed safeguarding-signal screening verdict for a case, or nil")
  (careplan-of [s case-id] "committed care-plan evidence assessment, or nil")
  (ledger [s])
  (dispatch-history [s] "the append-only check-in-dispatch history (care.registry drafts)")
  (closure-history [s] "the append-only case-closure history (care.registry drafts)")
  (next-dispatch-sequence [s jurisdiction] "next dispatch-number sequence for a jurisdiction")
  (next-closure-sequence [s jurisdiction] "next closure-number sequence for a jurisdiction")
  (case-already-dispatched? [s case-id] "has this case's check-in already been dispatched?")
  (case-already-closed? [s case-id] "has this case already been closed?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-cases [s cases] "replace/seed the case directory (map id->case)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained case set covering both actuation lifecycles
  (dispatching a check-in, closing a case) so the actor + tests run
  offline."
  []
  {:cases
   {"case-1" {:id "case-1" :recipient-name "Sato Kenji"
             :caregiver-current-caseload 5 :caregiver-max-caseload 8
             :safeguarding-signal-unresolved? false
             :checkin-dispatched? false :case-closed? false
             :jurisdiction "JPN" :status :intake}
    "case-2" {:id "case-2" :recipient-name "Atlantis Doe"
             :caregiver-current-caseload 5 :caregiver-max-caseload 8
             :safeguarding-signal-unresolved? false
             :checkin-dispatched? false :case-closed? false
             :jurisdiction "ATL" :status :intake}
    "case-3" {:id "case-3" :recipient-name "鈴木花子"
             :caregiver-current-caseload 10 :caregiver-max-caseload 8
             :safeguarding-signal-unresolved? false
             :checkin-dispatched? false :case-closed? false
             :jurisdiction "JPN" :status :intake}
    "case-4" {:id "case-4" :recipient-name "田中一郎"
             :caregiver-current-caseload 5 :caregiver-max-caseload 8
             :safeguarding-signal-unresolved? true
             :checkin-dispatched? false :case-closed? false
             :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- dispatch-checkin!
  "Backend-agnostic `:case/mark-dispatched` -- looks up the case via
  the protocol and drafts the check-in-dispatch record, and returns
  {:result .. :case-patch ..} for the caller to persist."
  [s case-id]
  (let [c (case-of s case-id)
        seq-n (next-dispatch-sequence s (:jurisdiction c))
        result (registry/register-checkin-dispatch case-id (:jurisdiction c) seq-n)]
    {:result result
     :case-patch {:checkin-dispatched? true
                 :dispatch-number (get result "dispatch_number")}}))

(defn- close-case!
  "Backend-agnostic `:case/mark-closed` -- looks up the case via the
  protocol and drafts the case-closure record, and returns {:result ..
  :case-patch ..} for the caller to persist."
  [s case-id]
  (let [c (case-of s case-id)
        seq-n (next-closure-sequence s (:jurisdiction c))
        result (registry/register-case-closure case-id (:jurisdiction c) seq-n)]
    {:result result
     :case-patch {:case-closed? true
                 :closure-number (get result "closure_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (case-of [_ id] (get-in @a [:cases id]))
  (all-cases [_] (sort-by :id (vals (:cases @a))))
  (safeguarding-screen-of [_ id] (get-in @a [:safeguarding-screens id]))
  (careplan-of [_ case-id] (get-in @a [:careplans case-id]))
  (ledger [_] (:ledger @a))
  (dispatch-history [_] (:dispatches @a))
  (closure-history [_] (:closures @a))
  (next-dispatch-sequence [_ jurisdiction] (get-in @a [:dispatch-sequences jurisdiction] 0))
  (next-closure-sequence [_ jurisdiction] (get-in @a [:closure-sequences jurisdiction] 0))
  (case-already-dispatched? [_ case-id] (boolean (get-in @a [:cases case-id :checkin-dispatched?])))
  (case-already-closed? [_ case-id] (boolean (get-in @a [:cases case-id :case-closed?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :case/upsert
      (swap! a update-in [:cases (:id value)] merge value)

      :careplan/set
      (swap! a assoc-in [:careplans (first path)] payload)

      :safeguarding-screen/set
      (swap! a assoc-in [:safeguarding-screens (first path)] payload)

      :case/mark-dispatched
      (let [case-id (first path)
            {:keys [result case-patch]} (dispatch-checkin! s case-id)
            jurisdiction (:jurisdiction (case-of s case-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:dispatch-sequences jurisdiction] (fnil inc 0))
                       (update-in [:cases case-id] merge case-patch)
                       (update :dispatches registry/append result))))
        result)

      :case/mark-closed
      (let [case-id (first path)
            {:keys [result case-patch]} (close-case! s case-id)
            jurisdiction (:jurisdiction (case-of s case-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:closure-sequences jurisdiction] (fnil inc 0))
                       (update-in [:cases case-id] merge case-patch)
                       (update :closures registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-cases [s cases] (when (seq cases) (swap! a assoc :cases cases)) s))

(defn seed-db
  "A MemStore seeded with the demo case set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :careplans {} :safeguarding-screens {} :ledger [] :dispatch-sequences {}
                           :dispatches [] :closure-sequences {} :closures []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Compound values (careplan/safeguarding-screen payloads, ledger facts,
  dispatch/closure records) are stored as EDN strings so `langchain.db`
  doesn't expand them into sub-entities -- the same convention every
  sibling actor's store uses."
  {:case/id                          {:db/unique :db.unique/identity}
   :careplan/case-id                 {:db/unique :db.unique/identity}
   :safeguarding-screen/case-id       {:db/unique :db.unique/identity}
   :ledger/seq                       {:db/unique :db.unique/identity}
   :dispatch/seq                     {:db/unique :db.unique/identity}
   :closure/seq                      {:db/unique :db.unique/identity}
   :dispatch-sequence/jurisdiction   {:db/unique :db.unique/identity}
   :closure-sequence/jurisdiction    {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- case->tx [{:keys [id recipient-name caregiver-current-caseload caregiver-max-caseload
                        safeguarding-signal-unresolved?
                        checkin-dispatched? case-closed?
                        jurisdiction status dispatch-number closure-number]}]
  (cond-> {:case/id id}
    recipient-name                              (assoc :case/recipient-name recipient-name)
    caregiver-current-caseload                  (assoc :case/caregiver-current-caseload caregiver-current-caseload)
    caregiver-max-caseload                       (assoc :case/caregiver-max-caseload caregiver-max-caseload)
    (some? safeguarding-signal-unresolved?)      (assoc :case/safeguarding-signal-unresolved? safeguarding-signal-unresolved?)
    (some? checkin-dispatched?)                  (assoc :case/checkin-dispatched? checkin-dispatched?)
    (some? case-closed?)                         (assoc :case/case-closed? case-closed?)
    jurisdiction                                 (assoc :case/jurisdiction jurisdiction)
    status                                       (assoc :case/status status)
    dispatch-number                              (assoc :case/dispatch-number dispatch-number)
    closure-number                               (assoc :case/closure-number closure-number)))

(def ^:private case-pull
  [:case/id :case/recipient-name :case/caregiver-current-caseload :case/caregiver-max-caseload
   :case/safeguarding-signal-unresolved? :case/checkin-dispatched? :case/case-closed?
   :case/jurisdiction :case/status :case/dispatch-number :case/closure-number])

(defn- pull->case [m]
  (when (:case/id m)
    {:id (:case/id m) :recipient-name (:case/recipient-name m)
     :caregiver-current-caseload (:case/caregiver-current-caseload m)
     :caregiver-max-caseload (:case/caregiver-max-caseload m)
     :safeguarding-signal-unresolved? (boolean (:case/safeguarding-signal-unresolved? m))
     :checkin-dispatched? (boolean (:case/checkin-dispatched? m))
     :case-closed? (boolean (:case/case-closed? m))
     :jurisdiction (:case/jurisdiction m) :status (:case/status m)
     :dispatch-number (:case/dispatch-number m) :closure-number (:case/closure-number m)}))

(defrecord DatomicStore [conn]
  Store
  (case-of [_ id]
    (pull->case (d/pull (d/db conn) case-pull [:case/id id])))
  (all-cases [_]
    (->> (d/q '[:find [?id ...] :where [?e :case/id ?id]] (d/db conn))
         (map #(pull->case (d/pull (d/db conn) case-pull [:case/id %])))
         (sort-by :id)))
  (safeguarding-screen-of [_ id]
    (dec* (d/q '[:find ?p . :in $ ?cid
                :where [?k :safeguarding-screen/case-id ?cid] [?k :safeguarding-screen/payload ?p]]
              (d/db conn) id)))
  (careplan-of [_ case-id]
    (dec* (d/q '[:find ?p . :in $ ?cid
                :where [?a :careplan/case-id ?cid] [?a :careplan/payload ?p]]
              (d/db conn) case-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (dispatch-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :dispatch/seq ?s] [?e :dispatch/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (closure-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :closure/seq ?s] [?e :closure/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-dispatch-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :dispatch-sequence/jurisdiction ?j] [?e :dispatch-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-closure-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :closure-sequence/jurisdiction ?j] [?e :closure-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (case-already-dispatched? [s case-id]
    (boolean (:checkin-dispatched? (case-of s case-id))))
  (case-already-closed? [s case-id]
    (boolean (:case-closed? (case-of s case-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :case/upsert
      (d/transact! conn [(case->tx value)])

      :careplan/set
      (d/transact! conn [{:careplan/case-id (first path) :careplan/payload (enc payload)}])

      :safeguarding-screen/set
      (d/transact! conn [{:safeguarding-screen/case-id (first path) :safeguarding-screen/payload (enc payload)}])

      :case/mark-dispatched
      (let [case-id (first path)
            {:keys [result case-patch]} (dispatch-checkin! s case-id)
            jurisdiction (:jurisdiction (case-of s case-id))
            next-n (inc (next-dispatch-sequence s jurisdiction))]
        (d/transact! conn
                     [(case->tx (assoc case-patch :id case-id))
                      {:dispatch-sequence/jurisdiction jurisdiction :dispatch-sequence/next next-n}
                      {:dispatch/seq (count (dispatch-history s)) :dispatch/record (enc (get result "record"))}])
        result)

      :case/mark-closed
      (let [case-id (first path)
            {:keys [result case-patch]} (close-case! s case-id)
            jurisdiction (:jurisdiction (case-of s case-id))
            next-n (inc (next-closure-sequence s jurisdiction))]
        (d/transact! conn
                     [(case->tx (assoc case-patch :id case-id))
                      {:closure-sequence/jurisdiction jurisdiction :closure-sequence/next next-n}
                      {:closure/seq (count (closure-history s)) :closure/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-cases [s cases]
    (when (seq cases) (d/transact! conn (mapv case->tx (vals cases)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:cases ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [cases]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-cases s cases))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo case set -- the Datomic-backed
  analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
