(ns care.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean case through
  intake -> care-plan verification -> safeguarding-signal screening ->
  check-in-dispatch proposal (always escalates) -> human approval ->
  commit, then through case-closure proposal (always escalates) ->
  human approval -> commit, then shows five HARD holds (a jurisdiction
  with no spec-basis, an overloaded caregiver's own workload exceeding
  their own maximum caseload, an unresolved safeguarding signal
  screened directly via `:safeguarding/screen` [never via an actuation
  op against an unscreened case -- see this actor's own governor ns
  docstring / the lesson `parksafety`'s ADR-2607071922 Decision 5,
  `eldercare`'s, `museum`'s, `conservation`'s, `salon`'s,
  `entertainment`'s, `casework`'s, `hospital`'s, `facility`'s,
  `school`'s, `association`'s, `leasing`'s, `behavioral`'s,
  `secondary`'s, `card`'s, `water`'s, `telecom`'s, `aerospace`'s,
  `recovery`'s, `consulting`'s, `union`'s, `congregation`'s, `fab`'s
  and `energy`'s ADR-0001s already recorded], and a double check-in-
  dispatch/case-closure of an already-processed case) that never reach
  a human at all, and prints the audit ledger + the draft check-in-
  dispatch and case-closure records."
  (:require [langgraph.graph :as g]
            [care.store :as store]
            [care.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :care-coordinator :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== case/intake case-1 (JPN, clean; caregiver within max caseload, no safeguarding signal) ==")
    (println (exec! actor "t1" {:op :case/intake :subject "case-1"
                                :patch {:id "case-1" :recipient-name "Sato Kenji"}} operator))

    (println "== careplan/verify case-1 (escalates -- human approves) ==")
    (println (exec! actor "t2" {:op :careplan/verify :subject "case-1"} operator))
    (println (approve! actor "t2"))

    (println "== safeguarding/screen case-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :safeguarding/screen :subject "case-1"} operator))
    (println (approve! actor "t3"))

    (println "== actuation/dispatch-checkin case-1 (always escalates -- actuation/dispatch-checkin) ==")
    (let [r (exec! actor "t4" {:op :actuation/dispatch-checkin :subject "case-1"} operator)]
      (println r)
      (println "-- human care-coordinator approves --")
      (println (approve! actor "t4")))

    (println "== actuation/close-case case-1 (always escalates -- actuation/close-case) ==")
    (let [r (exec! actor "t5" {:op :actuation/close-case :subject "case-1"} operator)]
      (println r)
      (println "-- human care-coordinator approves --")
      (println (approve! actor "t5")))

    (println "== careplan/verify case-2 (no spec-basis -> HARD hold) ==")
    (println (exec! actor "t6" {:op :careplan/verify :subject "case-2" :no-spec? true} operator))

    (println "== careplan/verify case-3 (escalates -- human approves; sets up the workload-exceeded test) ==")
    (println (exec! actor "t7" {:op :careplan/verify :subject "case-3"} operator))
    (println (approve! actor "t7"))

    (println "== actuation/dispatch-checkin case-3 (caregiver caseload 10 > max 8 -> HARD hold) ==")
    (println (exec! actor "t8" {:op :actuation/dispatch-checkin :subject "case-3"} operator))

    (println "== safeguarding/screen case-4 (unresolved -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t9" {:op :safeguarding/screen :subject "case-4"} operator))

    (println "== actuation/dispatch-checkin case-1 AGAIN (double-dispatch -> HARD hold) ==")
    (println (exec! actor "t10" {:op :actuation/dispatch-checkin :subject "case-1"} operator))

    (println "== actuation/close-case case-1 AGAIN (double-closure -> HARD hold) ==")
    (println (exec! actor "t11" {:op :actuation/close-case :subject "case-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft check-in-dispatch records ==")
    (doseq [r (store/dispatch-history db)] (println r))

    (println "== draft case-closure records ==")
    (doseq [r (store/closure-history db)] (println r))))
