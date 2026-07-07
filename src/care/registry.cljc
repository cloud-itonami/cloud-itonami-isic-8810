(ns care.registry
  "Pure-function check-in-dispatch + case-closure record construction
  -- an append-only community-care-coordination book-of-record draft.

  Like every sibling actor's registry, there is no single
  international check-digit standard for a check-in-dispatch or case-
  closure reference number -- every care-coordination provider/
  jurisdiction assigns its own reference format. This namespace does
  NOT invent one; it builds a jurisdiction-scoped sequence number and
  validates the record's required fields, the same honest, non-
  fabricating discipline `care.facts` uses.

  `caregiver-workload-exceeds-maximum?` is the FIFTH instance of this
  fleet's MAXIMUM-ceiling check family (`facility.registry/occupancy-
  exceeds-capacity?` established the first, `school.registry/class-
  size-exceeds-maximum?` the second, `card.registry/settlement-
  amount-exceeds-authorized?` the third, `recovery.registry/
  contamination-percentage-exceeds-maximum?` the fourth), applying the
  SAME lo-bound-absent/hi-bound-only comparison to a caregiver's own
  recorded current caseload against their own recorded maximum
  caseload -- a direct, natural mapping onto real caregiver-workload/
  burnout-prevention practice (dispatching an already-overloaded
  caregiver for one more in-home visit is exactly the failure mode a
  community-care operator must not let an advisor wave through).

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real care-coordination system. It builds the RECORD a
  care-coordination operator would keep, not the act of dispatching
  the check-in or closing the case itself (that is `care.operation`'s
  `:actuation/dispatch-checkin`/`:actuation/close-case`, always human-
  gated -- see README `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  care-coordination operator's own act, not this actor's. See README
  `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn caregiver-workload-exceeds-maximum?
  "Does `case`'s own assigned caregiver's `:caregiver-current-caseload`
  exceed their own recorded `:caregiver-max-caseload`? A pure ground-
  truth check against the case's own permanent fields -- no upstream
  comparison needed. The FIFTH instance of this fleet's MAXIMUM-
  ceiling check family (see ns docstring)."
  [{:keys [caregiver-current-caseload caregiver-max-caseload]}]
  (and (number? caregiver-current-caseload) (number? caregiver-max-caseload)
       (> caregiver-current-caseload caregiver-max-caseload)))

(defn register-checkin-dispatch
  "Validate + construct the CHECK-IN-DISPATCH registration DRAFT --
  the care-coordination operator's own act of dispatching a real
  robot/caregiver in-home check-in visit. Pure function -- does not
  touch any real care-coordination system; it builds the RECORD an
  operator would keep. `care.governor` independently re-verifies the
  assigned caregiver's own workload and blocks a double-dispatch for
  the same case, before this is ever allowed to commit."
  [case-id jurisdiction sequence]
  (when-not (and case-id (not= case-id ""))
    (throw (ex-info "checkin-dispatch: case_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "checkin-dispatch: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "checkin-dispatch: sequence must be >= 0" {})))
  (let [dispatch-number (str (str/upper-case jurisdiction) "-CHK-" (zero-pad sequence 6))
        record {"record_id" dispatch-number
                "kind" "checkin-dispatch-draft"
                "case_id" case-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "dispatch_number" dispatch-number
     "certificate" (unsigned-certificate "CheckinDispatch" dispatch-number dispatch-number)}))

(defn register-case-closure
  "Validate + construct the CASE-CLOSURE registration DRAFT -- the
  care-coordination operator's own act of discharging/closing a real
  case. Pure function -- does not touch any real care-coordination
  system; it builds the RECORD an operator would keep. `care.governor`
  independently re-verifies the case's own safeguarding-signal
  resolution status and blocks a double-closure for the same case,
  before this is ever allowed to commit."
  [case-id jurisdiction sequence]
  (when-not (and case-id (not= case-id ""))
    (throw (ex-info "case-closure: case_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "case-closure: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "case-closure: sequence must be >= 0" {})))
  (let [closure-number (str (str/upper-case jurisdiction) "-CLS-" (zero-pad sequence 6))
        record {"record_id" closure-number
                "kind" "case-closure-draft"
                "case_id" case-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "closure_number" closure-number
     "certificate" (unsigned-certificate "CaseClosure" closure-number closure-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
