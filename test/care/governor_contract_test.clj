(ns care.governor-contract-test
  "The governor contract as executable tests -- the care analog of
  `cloud-itonami-isic-6512`'s `casualty.governor-contract-test`. The
  single invariant under test:

    CareOps-LLM never dispatches a check-in or closes a case the
    Safeguarding Governor would reject, `:actuation/dispatch-checkin`/
    `:actuation/close-case` NEVER auto-commit at any phase, `:case/
    intake` (no direct capital risk) MAY auto-commit when clean, and
    every decision (commit OR hold) leaves exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [care.store :as store]
            [care.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :care-coordinator :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify!
  "Walks `subject` through verify -> approve, leaving a careplan on
  file. Uses distinct thread-ids per call site by suffixing
  `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :careplan/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify")))

(defn- screen!
  "Walks `subject` through safeguarding-signal screening -> approve,
  leaving a screening on file. Only safe to call for a case whose
  signal status has already resolved -- an unresolved signal HARD-
  holds the screen itself (see
  `safeguarding-signal-is-held-and-unoverridable`)."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-screen") {:op :safeguarding/screen :subject subject} operator)
  (approve! actor (str tid-prefix "-screen")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :case/intake :subject "case-1"
                   :patch {:id "case-1" :recipient-name "Sato Kenji"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Sato Kenji" (:recipient-name (store/case-of db "case-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest careplan-verify-always-needs-approval
  (testing "verify is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :careplan/verify :subject "case-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/careplan-of db "case-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a careplan/verify proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :careplan/verify :subject "case-1" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/careplan-of db "case-1")) "no careplan written"))))

(deftest dispatch-checkin-without-careplan-is-held
  (testing "actuation/dispatch-checkin before any careplan verification -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :actuation/dispatch-checkin :subject "case-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest caregiver-workload-exceeds-maximum-is-held
  (testing "a case whose assigned caregiver's own current caseload exceeds their own max caseload -> HOLD"
    (let [[db actor] (fresh)
          _ (verify! actor "t5pre" "case-3")
          res (exec-op actor "t5" {:op :actuation/dispatch-checkin :subject "case-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:caregiver-workload-exceeds-maximum} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest safeguarding-signal-is-held-and-unoverridable
  (testing "an unresolved safeguarding signal on a case -> HOLD, and never reaches request-approval -- exercised via :safeguarding/screen DIRECTLY, not via the actuation op against an unscreened case (see this actor's governor ns docstring / parksafety's ADR-2607071922 Decision 5 / eldercare's, museum's, conservation's, salon's, entertainment's, casework's, hospital's, facility's, school's, association's, leasing's, behavioral's, secondary's, card's, water's, telecom's, aerospace's, recovery's, consulting's, union's, congregation's, fab's and energy's ADR-0001s)"
    (let [[db actor] (fresh)
          res (exec-op actor "t6" {:op :safeguarding/screen :subject "case-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:safeguarding-signal-unresolved} (-> (store/ledger db) first :basis)))
      (is (nil? (store/safeguarding-screen-of db "case-4")) "no clearance written"))))

(deftest dispatch-checkin-always-escalates-then-human-decides
  (testing "a clean, fully-assessed case still ALWAYS interrupts for human approval -- actuation/dispatch-checkin is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t7pre" "case-1")
          r1 (exec-op actor "t7" {:op :actuation/dispatch-checkin :subject "case-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, dispatch record drafted"
        (let [r2 (approve! actor "t7")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:checkin-dispatched? (store/case-of db "case-1"))))
          (is (= 1 (count (store/dispatch-history db))) "one draft dispatch record"))))))

(deftest close-case-always-escalates-then-human-decides
  (testing "a clean, fully-assessed, safeguarding-resolved case still ALWAYS interrupts for human approval -- actuation/close-case is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t8pre" "case-1")
          _ (screen! actor "t8pre2" "case-1")
          r1 (exec-op actor "t8" {:op :actuation/close-case :subject "case-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, closure record drafted"
        (let [r2 (approve! actor "t8")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:case-closed? (store/case-of db "case-1"))))
          (is (= 1 (count (store/closure-history db))) "one draft closure record"))))))

(deftest dispatch-checkin-double-dispatch-is-held
  (testing "dispatching the same case's check-in twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t9pre" "case-1")
          _ (exec-op actor "t9a" {:op :actuation/dispatch-checkin :subject "case-1"} operator)
          _ (approve! actor "t9a")
          res (exec-op actor "t9" {:op :actuation/dispatch-checkin :subject "case-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-dispatched} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/dispatch-history db))) "still only the one earlier dispatch"))))

(deftest close-case-double-closure-is-held
  (testing "closing the same case twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t10pre" "case-1")
          _ (screen! actor "t10pre2" "case-1")
          _ (exec-op actor "t10a" {:op :actuation/close-case :subject "case-1"} operator)
          _ (approve! actor "t10a")
          res (exec-op actor "t10" {:op :actuation/close-case :subject "case-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-closed} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/closure-history db))) "still only the one earlier closure"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :case/intake :subject "case-1"
                          :patch {:id "case-1" :recipient-name "Sato Kenji"}} operator)
      (exec-op actor "b" {:op :careplan/verify :subject "case-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
