(ns care.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 for `cloud-itonami-isic-8810`
  (com-junkawasaki/root ADR-2607189300). This repo DID ship a
  `docs/samples/operator-console.html`, but it had no generator and was
  not produced by this actor at all -- it was a hand-typed page titled
  \"Robotics Safety -- Operator Console\" listing missions `M1` and a
  robot `robot-1`, i.e. boilerplate from an entirely different domain
  with entity ids that appear NOWHERE in `care.store/demo-data`. It has
  been replaced by the real output of this namespace.

  This namespace drives the REAL actor stack (`care.operation` ->
  `care.governor` -> `care.store`) through a scenario extended from
  this repo's own `care.sim` demo driver (`clojure -M:dev:run`), and
  renders the resulting store + run audit deterministically. Every
  case id, recipient name, caseload figure, dispatch/closure reference
  number, jurisdiction citation, hold rule and hold detail string on
  the page is read back out of the seeded store or out of governor
  output -- nothing is hand-typed. This matters more here than in most
  siblings: ISIC 8810 is social work without accommodation, so an
  invented case fact is an invented vulnerable adult.

  Determinism: no timestamps, no randomness, no map-order dependence
  (cases come back `sort-by :id` from the store, registers are walked
  in that same case order, the jurisdiction catalog is walked in sorted
  key order, and the ledger is append-ordered). Byte-identical across
  reruns -- verify by rendering twice into a scratch dir and diffing.

  Build-time invariant: `-main` REFUSES to write a page whose run
  produced no `:governor-hold` ledger fact, and refuses again if none
  of them carry governor violations (i.e. if the only holds were
  rollout-phase gating rather than real HARD compliance holds). A
  console that shows an actor which never says no is not evidence of a
  governor.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [care.facts :as facts]
            [care.governor :as governor]
            [care.phase :as phase]
            [care.registry :as registry]
            [care.store :as store]
            [care.operation :as op]
            [langgraph.graph :as g]))

;; ----------------------------- the run -----------------------------

(def ^:private coordinator
  "The phase-3 care coordinator who drives the main scenario."
  {:actor-id "op-1" :actor-role :care-coordinator :phase 3})

(defn run-demo!
  "Runs a fresh seeded store through a scenario that exercises every
  disposition this actor can reach.

  `case-1` (JPN, caregiver 5/8, no safeguarding signal) clears a full
  lifecycle: intake (auto-commits at phase 3 -- the only op in any
  phase's `:auto` set), a care-plan evidence verification (phase-gated,
  approved), a safeguarding screening (approved), a check-in dispatch
  and a case closure (both ALWAYS escalate -- `:actuation/dispatch-
  checkin` / `:actuation/close-case` are never auto-eligible at any
  phase AND are independently high-stakes to the governor -- approved).

  Six distinct HARD governor holds, none of which ever reaches a
  human: `case-2` is in an unregistered jurisdiction so its care-plan
  proposal has no official spec-basis (`:no-spec-basis`) and a
  subsequent check-in dispatch has no verified evidence checklist on
  file (`:evidence-incomplete`); `case-3` clears its own care-plan
  verification but its assigned caregiver's own recorded caseload (10)
  exceeds their own recorded maximum (8), independently recomputed by
  the governor (`:caregiver-workload-exceeds-maximum`); `case-4`'s
  screening detects an unresolved safeguarding signal
  (`:safeguarding-signal-unresolved`); and re-running `case-1`'s
  dispatch and closure is refused off dedicated boolean facts
  (`:already-dispatched`, `:already-closed`).

  One human REJECTION (`case-4`'s care-plan verification) shows the
  approval gate is a real decision, not a rubber stamp, and three runs
  by lower-phase operators show the rollout gate holding or escalating
  work the phase-3 coordinator is allowed to do.

  Returns {:db store :runs [step ..]} -- `runs` is the real per-step
  outcome of each `g/run*`, used for the timeline and to join approver
  identity that the store does not itself retain."
  []
  (let [db     (store/seed-db)
        actor  (op/build db)
        runs   (atom [])
        record! (fn [label ctx {:keys [op subject]} result]
                  (swap! runs conj
                         {:step       (inc (count @runs))
                          :label      label
                          :thread     (:thread result)
                          :actor      (:actor-id ctx)
                          :phase      (:phase ctx)
                          :op         op
                          :subject    subject
                          :status     (:status result)
                          :disposition (get-in result [:state :disposition])
                          :audit      (get-in result [:state :audit])})
                  result)
        exec!  (fn exec-fn
                 ([label tid request] (exec-fn label tid request coordinator))
                 ([label tid request ctx]
                  (let [r (g/run* actor {:request request :context ctx} {:thread-id tid})]
                    (record! label ctx request (assoc r :thread tid)))))
        resume! (fn [label tid request approval]
                  (let [r (g/run* actor {:approval approval} {:thread-id tid :resume? true})]
                    (record! label coordinator request (assoc r :thread tid))))]

    ;; --- case-1: the full clean lifecycle -------------------------------
    (exec! "intake" "t1-intake"
           {:op :case/intake :subject "case-1"
            :patch {:id "case-1" :recipient-name "Sato Kenji"}})

    (let [req {:op :careplan/verify :subject "case-1"}]
      (exec! "care-plan evidence verification" "t1-careplan" req)
      (resume! "care-plan approved" "t1-careplan" req {:status :approved :by "op-1"}))

    (let [req {:op :safeguarding/screen :subject "case-1"}]
      (exec! "safeguarding screening" "t1-screen" req)
      (resume! "screening approved" "t1-screen" req {:status :approved :by "op-1"}))

    (let [req {:op :actuation/dispatch-checkin :subject "case-1"}]
      (exec! "check-in dispatch proposal" "t1-dispatch" req)
      (resume! "dispatch approved" "t1-dispatch" req {:status :approved :by "op-1"}))

    (let [req {:op :actuation/close-case :subject "case-1"}]
      (exec! "case-closure proposal" "t1-close" req)
      (resume! "closure approved" "t1-close" req {:status :approved :by "op-1"}))

    ;; --- HARD holds -----------------------------------------------------
    (exec! "care-plan on an unregistered jurisdiction" "t2-careplan"
           {:op :careplan/verify :subject "case-2" :no-spec? true})

    (exec! "dispatch with no verified evidence on file" "t2-dispatch"
           {:op :actuation/dispatch-checkin :subject "case-2"})

    (let [req {:op :careplan/verify :subject "case-3"}]
      (exec! "care-plan evidence verification" "t3-careplan" req)
      (resume! "care-plan approved" "t3-careplan" req {:status :approved :by "op-1"}))

    (exec! "dispatch to an over-caseload caregiver" "t3-dispatch"
           {:op :actuation/dispatch-checkin :subject "case-3"})

    (exec! "safeguarding screening" "t4-screen"
           {:op :safeguarding/screen :subject "case-4"})

    (exec! "second check-in dispatch for the same case" "t1-dispatch-again"
           {:op :actuation/dispatch-checkin :subject "case-1"})

    (exec! "second closure for the same case" "t1-close-again"
           {:op :actuation/close-case :subject "case-1"})

    ;; --- a human says no ------------------------------------------------
    (let [req {:op :careplan/verify :subject "case-4"}]
      (exec! "care-plan evidence verification" "t4-careplan" req)
      (resume! "care-plan REJECTED by approver" "t4-careplan" req
               {:status :rejected :by "op-1"}))

    ;; --- the rollout gate, driven by lower-phase operators ---------------
    (exec! "care-plan attempt at phase 0" "p0-careplan"
           {:op :careplan/verify :subject "case-1"}
           {:actor-id "op-phase0" :actor-role :care-coordinator :phase 0})

    (exec! "screening attempt at phase 1" "p1-screen"
           {:op :safeguarding/screen :subject "case-1"}
           {:actor-id "op-phase1" :actor-role :care-coordinator :phase 1})

    (exec! "screening attempt at phase 2" "p2-screen"
           {:op :safeguarding/screen :subject "case-1"}
           {:actor-id "op-phase2" :actor-role :care-coordinator :phase 2})

    {:db db :runs @runs}))

;; ----------------------------- html helpers -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- code [v] (str "<code>" (esc v) "</code>"))

(defn- nm [v] (if (keyword? v) (str (symbol v)) (str v)))

(defn- row [& cells] (str "        <tr>" (str/join (map #(str "<td>" % "</td>") cells)) "</tr>"))

(defn- rows [xs] (str/join "\n" xs))

(defn- table [headers body-rows]
  (str "    <table>\n"
       "      <thead><tr>" (str/join (map #(str "<th>" % "</th>") headers)) "</tr></thead>\n"
       "      <tbody>\n" (rows body-rows) "\n      </tbody>\n"
       "    </table>\n"))

(defn- section [title lede content]
  (str "  <section class=\"card\">\n"
       "    <h2>" title "</h2>\n"
       (when lede (str "    <p class=\"muted\">" lede "</p>\n"))
       content
       "  </section>\n"))

(defn- pill [class label] (str "<span class=\"" class "\">" label "</span>"))

;; ----------------------------- derived views -----------------------------

(defn- hard-holds
  "Ledger facts that are governor HOLDs carrying at least one
  violation. A rollout-phase hold also lands as `:governor-hold` but
  with an empty `:violations`, and an approver rejection lands as
  `:approval-rejected` -- neither is a HARD compliance hold, so both
  are excluded here and shown in their own sections."
  [ledger]
  (filter #(and (= :governor-hold (:t %)) (seq (:violations %))) ledger))

(defn- phase-holds [ledger]
  (filter #(and (= :governor-hold (:t %)) (empty? (:violations %))) ledger))

(defn- rejections [ledger]
  (filter #(= :approval-rejected (:t %)) ledger))

(defn- run-audit
  "Every audit fact the run itself produced, in step order. The store
  ledger keeps only committed facts and holds; `:approval-granted` /
  `:approval-requested` / `:careadvisor-proposal` facts live only in
  the graph's `:audit` channel, so they are read back from the run
  results. Each thread's `:audit` channel accumulates, so only the LAST
  state per thread is taken (in first-appearance order) -- otherwise
  resumed threads would double-count their pre-interrupt facts."
  [runs]
  (let [order (distinct (map :thread runs))
        last-by (reduce (fn [m r] (assoc m (:thread r) r)) {} runs)]
    (mapcat #(:audit (get last-by %)) order)))

(defn- approvals
  "[op subject] -> approver id, from the run's own `:approval-granted`
  audit facts."
  [audit]
  (reduce (fn [m {:keys [t op subject by]}]
            (if (= :approval-granted t) (assoc m [op subject] by) m))
          {} audit))

(defn- approver-key
  "MEASURED, not assumed: which key (if any) in this committed artifact
  actually carries the approver? Returns the key or nil. Some stores in
  this fleet drop the approval payload on the way to the SSoT; whether
  THIS store does is a property of the run, so it is read off the run's
  own output rather than asserted in prose."
  [artifact]
  (when (map? artifact)
    (some #(when (contains? artifact %) %)
          [:approved-by "approved_by" "approved-by" :approver])))

(defn- attribution
  "One row per committed artifact: did the SSoT retain who approved it,
  and if not, can the approver still be named from the run audit?"
  [db cases audit]
  (let [appr (approvals audit)
        base (fn [register op subject artifact]
               (let [k (approver-key artifact)]
                 {:register register :op op :subject subject
                  :retained-key k
                  :retained (when k (get artifact k))
                  :audit-approver (get appr [op subject])}))]
    (concat
     (for [c cases :let [cp (store/careplan-of db (:id c))] :when cp]
       (base "care-plan evidence" :careplan/verify (:id c) cp))
     (for [c cases :let [sc (store/safeguarding-screen-of db (:id c))] :when sc]
       (base "safeguarding screening" :safeguarding/screen (:id c) sc))
     (for [r (store/dispatch-history db)]
       (base "check-in dispatch" :actuation/dispatch-checkin (get r "case_id") r))
     (for [r (store/closure-history db)]
       (base "case closure" :actuation/close-case (get r "case_id") r)))))

(defn- approver-cell [{:keys [retained audit-approver]}]
  (cond
    retained       (str (esc retained) " " (pill "ok" "retained in record"))
    audit-approver (str (esc audit-approver)
                        " <span class=\"warn\">(audit only; not retained in record)</span>")
    :else          (pill "muted" "no approver — this artifact was never approved by a human")))

;; ----------------------------- sections -----------------------------

(defn- summary-section [db cases ledger holds]
  (let [cov (facts/coverage (distinct (map :jurisdiction cases)))]
    (section
     "This run at a glance"
     (str "Every figure below is counted from the store and audit ledger this build actually produced — "
          "re-running the generator recomputes them.")
     (table ["Measure" "Value"]
            [(row "Cases in the SSoT" (str "<span class=\"num\">" (count cases) "</span>"))
             (row "Audit-ledger facts" (str "<span class=\"num\">" (count ledger) "</span>"))
             (row "HARD governor holds (un-overridable)"
                  (str "<span class=\"num\">" (count holds) "</span> across "
                       "<span class=\"num\">" (count (distinct (mapcat :basis holds))) "</span> distinct rules"))
             (row "Check-in dispatches committed"
                  (str "<span class=\"num\">" (count (store/dispatch-history db)) "</span>"))
             (row "Case closures committed"
                  (str "<span class=\"num\">" (count (store/closure-history db)) "</span>"))
             (row "Jurisdictions with an official spec-basis"
                  (str "<span class=\"num\">" (:covered cov) "</span> of "
                       "<span class=\"num\">" (:requested cov) "</span> seen in these cases"
                       (when (seq (:missing-jurisdictions cov))
                         (str " &middot; missing: "
                              (str/join ", " (map code (:missing-jurisdictions cov)))))))]))))

(defn- case-cell [{:keys [checkin-dispatched? case-closed?]}]
  (cond
    case-closed?        (pill "ok" "closed")
    checkin-dispatched? (pill "warn" "check-in dispatched, open")
    :else               (pill "muted" "open")))

(defn- caseload-cell [{:keys [caregiver-current-caseload caregiver-max-caseload] :as c}]
  (let [txt (str "<span class=\"num\">" (esc caregiver-current-caseload)
                 " / " (esc caregiver-max-caseload) "</span>")]
    (cond
      ;; un-checkable is NOT within limits -- the governor treats a missing
      ;; figure as a hold, so the console must not draw it as headroom.
      (not (registry/caregiver-workload-exceeds-maximum-checkable? c))
      (str txt " " (pill "critical" "not checkable"))

      (registry/caregiver-workload-exceeds-maximum? c)
      (str txt " " (pill "critical" "over maximum"))

      :else (str txt " " (pill "ok" "within maximum")))))

(defn- cases-section [cases]
  (section
   "Cases in the SSoT"
   (str "Seeded case directory read back through the "
        (code "care.store/Store") " protocol after the run. "
        "Caseload is the case's own assigned caregiver's recorded current / maximum caseload — "
        "the figures the governor independently recomputes before it will let a check-in be dispatched.")
   (table ["Case" "Recipient" "Jurisdiction" "Caregiver caseload" "Safeguarding signal" "Lifecycle" "Last committed reference"]
          (for [c cases]
            (row (code (:id c))
                 (esc (:recipient-name c))
                 (code (:jurisdiction c))
                 (caseload-cell c)
                 (if (:safeguarding-signal-unresolved? c)
                   (pill "critical" "unresolved")
                   (pill "ok" "none on file"))
                 (case-cell c)
                 (str/join " "
                           (remove nil?
                                   [(when-let [d (:dispatch-number c)] (code d))
                                    (when-let [x (:closure-number c)] (code x))])))))))

(defn- hard-holds-section [holds]
  (section
   "HARD governor holds — the actor refusing to act"
   (str "These never reach a human. "
        (code "care.governor") " returns "
        (code ":hard? true") " and " (code "care.phase/gate")
        " keeps a governor hold a hold at every phase, so there is no approval path around any of them. "
        "Rule names and detail strings are the governor's own output, verbatim.")
   (table ["Rule" "Op" "Case" "Governor detail" "Advisor confidence"]
          (for [f holds
                v (:violations f)]
            (row (pill "critical" (esc (nm (:rule v))))
                 (code (nm (:op f)))
                 (code (:subject f))
                 (esc (:detail v))
                 (str "<span class=\"num\">" (esc (:confidence f)) "</span>"))))))

(defn- timeline-section [runs]
  (section
   "Operation timeline"
   (str "Every " (code "langgraph.graph/run*") " call this build made, in order. "
        "One thread id = one care operation; a thread that shows "
        (code "interrupted") " is parked at " (code ":request-approval")
        " waiting on a human, and the next row is that human's decision.")
   (table ["#" "Step" "Op" "Case" "Operator (phase)" "Thread" "Graph status" "Disposition"]
          (for [r runs]
            (row (str "<span class=\"num\">" (:step r) "</span>")
                 (esc (:label r))
                 (code (nm (:op r)))
                 (code (:subject r))
                 (str (esc (:actor r)) " <span class=\"muted\">(phase "
                      (esc (:phase r)) ")</span>")
                 (code (:thread r))
                 (if (= :interrupted (:status r))
                   (pill "warn" "interrupted")
                   (pill "muted" "done"))
                 (case (:disposition r)
                   :commit   (pill "ok" "commit")
                   :hold     (pill "critical" "HOLD")
                   :escalate (pill "warn" "escalate → human")
                   (pill "muted" "—")))))))

(defn- phase-section [phase-hold-facts runs]
  (let [pending (filter #(and (= :escalate (:disposition %)) (< (:phase %) 3)) runs)]
    (section
     "Rollout phase gate"
     (str "The same actor, driven by operators at lower rollout phases. "
          (code "care.phase/gate") " can only add caution: it never turns a governor hold into a commit. "
          "The first table is the phase declaration read straight out of " (code "care.phase/phases")
          "; the second is what these operators actually got, with each gate reason read back out of "
          "the audit fact that run wrote.")
     (str
      (table ["Phase" "Label" "Ops allowed to write" "Ops allowed to auto-commit"]
             (for [p (sort (keys phase/phases))
                   :let [{:keys [label writes auto]} (get phase/phases p)]]
               (row (str "<span class=\"num\">" p "</span>"
                         (when (= p phase/default-phase)
                           (str " " (pill "ok" "default"))))
                    (esc label)
                    (if (seq writes)
                      (str/join " " (map (comp code nm) (sort-by nm writes)))
                      (pill "muted" "none"))
                    (if (seq auto)
                      (str/join " " (map (comp code nm) (sort-by nm auto)))
                      (pill "muted" "none")))))
      "    <p class=\"muted\">Observed in this run:</p>\n"
      (table ["Operator" "Op" "Case" "Gate reason" "Outcome"]
             (concat
              (for [f phase-hold-facts]
                (row (esc (:actor f))
                     (code (nm (:op f)))
                     (code (:subject f))
                     (code (nm (:phase-reason f)))
                     (pill "critical" "HOLD — phase not yet enabled")))
              (for [r pending
                    :let [reason (:reason (last (filter #(= :approval-requested (:t %))
                                                        (:audit r))))]]
                (row (esc (:actor r))
                     (code (nm (:op r)))
                     (code (:subject r))
                     ;; read back out of the run's own :approval-requested
                     ;; audit fact, so this column is measured the same way
                     ;; the hold rows above it are -- one column, one
                     ;; provenance.
                     (if reason (code (nm reason)) (pill "muted" "not recorded"))
                     (pill "warn" "escalate — awaiting a human, never approved in this run")))))))))

(defn- rejection-section [rejects]
  (when (seq rejects)
    (section
     "Human rejections"
     (str "The approval gate is a decision, not a rubber stamp. A rejected proposal falls back to HOLD "
          "and is written to the append-only ledger with the same shape as a governor hold, "
          "so a later dispute can see that a human was asked and said no.")
     (table ["Op" "Case" "Basis" "Outcome"]
            (for [f rejects]
              (row (code (nm (:op f)))
                   (code (:subject f))
                   (str/join ", " (map (comp code nm) (:basis f)))
                   (pill "critical" "HOLD — approver rejected")))))))

(defn- careplan-section [db cases attrib]
  (let [by-subject (into {} (for [a attrib :when (= "care-plan evidence" (:register a))]
                              [(:subject a) a]))]
    (section
     "Care-plan evidence register"
     (str "Committed " (code ":careplan/set") " records. The checklist is the jurisdiction's own "
          "required-evidence list from " (code "care.facts")
          " — the governor will not let a check-in be dispatched or a case be closed until every item is satisfied.")
     (table ["Case" "Jurisdiction" "Required evidence" "Spec-basis (official source)" "Approved by"]
            (for [c cases
                  :let [cp (store/careplan-of db (:id c))]
                  :when cp]
              (row (code (:id c))
                   (code (:jurisdiction cp))
                   (if (seq (:checklist cp))
                     (str "<span class=\"num\">" (count (:checklist cp)) "</span> · "
                          (esc (str/join " / " (:checklist cp))))
                     (pill "critical" "none"))
                   (if (:spec-basis cp)
                     (str "<code>" (esc (:spec-basis cp)) "</code>")
                     (pill "critical" "none — fabricated basis refused"))
                   (approver-cell (get by-subject (:id c)))))))))

(defn- screening-section [db cases attrib]
  (let [by-subject (into {} (for [a attrib :when (= "safeguarding screening" (:register a))]
                              [(:subject a) a]))]
    (section
     "Safeguarding screening register"
     (str "Committed " (code ":safeguarding-screen/set") " records. A screening whose own verdict is "
          (code ":unresolved") " HARD-holds on itself — the finding never commits, so an unresolved "
          "signal can never be quietly filed away as screened.")
     (table ["Case" "Recipient" "Verdict" "Approved by"]
            (for [c cases
                  :let [sc (store/safeguarding-screen-of db (:id c))]
                  :when sc]
              (row (code (:id c))
                   (esc (:recipient-name c))
                   (if (= :resolved (:verdict sc))
                     (pill "ok" "resolved")
                     (pill "critical" (esc (nm (:verdict sc)))))
                   (approver-cell (get by-subject (:id c)))))))))

(defn- register-section [title lede records id-key attrib register]
  (let [by-case (into {} (for [a attrib :when (= register (:register a))] [(:subject a) a]))]
    (section
     title lede
     (table ["Reference" "Kind" "Case" "Jurisdiction" "Immutable" "Approved by"]
            (for [r records]
              (row (code (get r id-key))
                   (esc (get r "kind"))
                   (code (get r "case_id"))
                   (code (get r "jurisdiction"))
                   (if (get r "immutable") (pill "ok" "yes") (pill "warn" "no"))
                   (approver-cell (get by-case (get r "case_id")))))))))

(defn- attribution-section [attrib]
  (let [retained (filter :retained-key attrib)
        audit-only (filter #(and (nil? (:retained-key %)) (:audit-approver %)) attrib)
        none (filter #(and (nil? (:retained-key %)) (nil? (:audit-approver %))) attrib)]
    (section
     "Approver attribution — measured, not assumed"
     (str "Who approved a committed artifact is checked at render time by walking each committed record "
          "and looking for an approver key, rather than asserting a fixed claim about this store. "
          "Where the approver is missing from the record it is named from the run's own "
          (code ":approval-granted") " audit facts and labelled as such — silently omitting it would make "
          "\"nobody approved this\" and \"the store did not keep who approved this\" look identical, "
          "which in a safeguarding domain is the whole point of the console.")
     (str
      (table ["Register" "Committed artifacts" "Approver in the record?" "Reading"]
             (for [[register items] (sort-by first (group-by :register attrib))]
               (let [k (some :retained-key items)]
                 (row (esc register)
                      (str "<span class=\"num\">" (count items) "</span>")
                      (if k
                        (str (pill "ok" "yes") " under " (code (nm k)))
                        (pill "warn" "no"))
                      (cond
                        k (str "the approval payload survives into the SSoT for this register")
                        (some :audit-approver items)
                        (str "the record this register keeps is built by " (code "care.registry")
                             " from the case + a jurisdiction sequence number only, so the approver is "
                             "recoverable from the run audit but is not retained in the SSoT")
                        :else "no artifact in this register was approved by a human in this run")))))
      "    <p class=\"muted\">Per-artifact: "
      "<span class=\"num\">" (count retained) "</span> retained in the record · "
      "<span class=\"num\">" (count audit-only) "</span> audit-only · "
      "<span class=\"num\">" (count none) "</span> never approved.</p>\n"))))

(defn- gate-section []
  (section
   "Op contract (Safeguarding Governor × rollout phase)"
   (str "Fixed behaviour of this actor's own closed op set, as declared by "
        (code "care.governor") " and " (code "care.phase") " — described here, "
        "not measured, because it is a property of the code rather than of this run. "
        "The confidence floor is " (code (str governor/confidence-floor))
        " and the permanently high-stakes ops are "
        (str/join ", " (map (comp code nm) (sort-by nm governor/high-stakes))) ".")
   (table ["Op" "Gate"]
          [(row (code ":case/intake")
                (pill "ok" "phase-3 auto-commit when governor-clean — the only auto-eligible op"))
           (row (code ":careplan/verify")
                (pill "warn" "human approval at every phase · HARD-holds a fabricated spec-basis"))
           (row (code ":safeguarding/screen")
                (pill "warn" "human approval at every phase · HARD-holds on its own unresolved finding"))
           (row (code ":actuation/dispatch-checkin")
                (pill "warn" "ALWAYS human approval · never auto at any phase · evidence checklist, caregiver caseload and double-dispatch independently re-checked"))
           (row (code ":actuation/close-case")
                (pill "warn" "ALWAYS human approval · never auto at any phase · evidence checklist, safeguarding signal and double-closure independently re-checked"))])))

(defn- jurisdiction-section [cases]
  (let [cov (facts/coverage (distinct (map :jurisdiction cases)))]
    (section
     "Jurisdiction spec-basis catalog"
     (str "The regulatory table the governor checks a care-plan proposal against. "
          "A jurisdiction that is not in this table has NO spec-basis, full stop — the advisor "
          "must not invent one, and the governor holds if it tries. Coverage is reported honestly: "
          (code (str (:covered cov) "/" (:requested cov)))
          " of the jurisdictions appearing in these cases are covered"
          (when (seq (:missing-jurisdictions cov))
            (str ", missing " (str/join ", " (map code (:missing-jurisdictions cov)))))
          ".")
     (table ["ISO3" "Jurisdiction" "Owner authority" "Legal basis" "Required evidence" "Provenance"]
            (for [iso3 (sort (keys facts/catalog))
                  :let [m (get facts/catalog iso3)]]
              (row (code iso3)
                   (esc (:name m))
                   (esc (:owner-authority m))
                   (esc (:legal-basis m))
                   (str "<span class=\"num\">" (count (:required-evidence m)) "</span> · "
                        (esc (str/join " / " (:required-evidence m))))
                   (str "<code>" (esc (:provenance m)) "</code>")))))))

(defn- ledger-section [ledger]
  (section
   "Audit ledger (append-only)"
   (str "Every decision fact this run wrote to the SSoT, in order. Holds are recorded as durably as "
        "commits — \"which case was screened, which check-in was dispatched, which case was closed, "
        "on what jurisdictional basis, and what was refused\" is always a query over an immutable log.")
   (table ["#" "Fact" "Op" "Case" "Actor" "Disposition" "Basis / summary"]
          (map-indexed
           (fn [i {:keys [t op subject actor disposition basis summary]}]
             (row (str "<span class=\"num\">" (inc i) "</span>")
                  (case t
                    :committed         (pill "ok" "committed")
                    :governor-hold     (pill "critical" "governor-hold")
                    :approval-rejected (pill "critical" "approval-rejected")
                    (pill "muted" (esc (nm t))))
                  (code (nm op))
                  (code subject)
                  (esc actor)
                  (esc (nm disposition))
                  ;; a hold's basis is the governor's rule list -- the thing a
                  ;; disputing reader needs; a commit's basis is the advisor's
                  ;; cites, which are already shown in the registers above, so
                  ;; the human-facing summary is the useful column there.
                  (if (= :committed t)
                    (esc (or summary ""))
                    (str/join ", " (map (comp code nm) basis)))))
           ledger))))

;; ----------------------------- document -----------------------------

(defn render
  "Renders the whole operator console from a `run-demo!` result."
  [{:keys [db runs]}]
  (let [ledger (vec (store/ledger db))
        cases  (vec (store/all-cases db))
        audit  (vec (run-audit runs))
        holds  (vec (hard-holds ledger))
        attrib (vec (attribution db cases audit))]
    (str
     "<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">\n"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
     "<title>cloud-itonami-isic-8810 &middot; community care coordination &mdash; Operator Console</title>\n"
     "<style>" (jp-go-dds.skin/dds+skin) "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Social work without accommodation (ISIC 8810) &mdash; Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample &middot; governor-gated &middot; check-in dispatch and case closure are always a human call</span>\n"
     "</header>\n"
     "<p class=\"subtitle\">Generated at build time by <code>care.render-html</code> from a real run of "
     "<code>care.operation</code> &rarr; <code>care.governor</code> &rarr; <code>care.store</code>. "
     "Every case id, name, caseload figure, reference number, citation and hold reason below was read back "
     "out of the seeded store or out of governor output &mdash; none of it is hand-written.</p>\n"
     "<main>\n"
     (summary-section db cases ledger holds)
     (cases-section cases)
     (hard-holds-section holds)
     (timeline-section runs)
     (phase-section (phase-holds ledger) runs)
     (rejection-section (rejections ledger))
     (gate-section)
     (careplan-section db cases attrib)
     (screening-section db cases attrib)
     (register-section
      "Check-in dispatch register"
      (str "Committed check-in-dispatch DRAFT records built by " (code "care.registry")
           ". Reference numbers are jurisdiction-scoped sequences this operator assigns — there is no "
           "international check-digit standard for a check-in dispatch, and this actor does not invent one. "
           "Every record is a draft an operator would keep; the certificate it carries is UNSIGNED, because "
           "signing is the care-coordination operator's own act, not this actor's.")
      (store/dispatch-history db) "record_id" attrib "check-in dispatch")
     (register-section
      "Case closure register"
      (str "Committed case-closure DRAFT records, same discipline as the dispatch register. "
           "A closure is refused outright while any safeguarding signal on the case is unresolved.")
      (store/closure-history db) "record_id" attrib "case closure")
     (attribution-section attrib)
     (jurisdiction-section cases)
     (ledger-section ledger)
     "</main>\n"
     "<footer>\n"
     "  <p>Regenerate with <code>clojure -M:dev:render-html</code>. Deterministic: no timestamps, no "
     "randomness, byte-identical across reruns against the same seed. The build refuses to write this page "
     "if the run produced no HARD governor hold &mdash; an actor that never says no is not evidence of a "
     "governor.</p>\n"
     "</footer>\n"
     "</body></html>\n")))

;; ----------------------------- entry point -----------------------------

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [db runs] :as result} (run-demo!)
        ledger (vec (store/ledger db))
        gov-holds (filter #(= :governor-hold (:t %)) ledger)
        hard (hard-holds ledger)
        rules (distinct (mapcat :basis hard))]
    ;; Build-time invariant, not a convention: a console rendered from a
    ;; run in which the governor never refused anything would be a
    ;; picture of an ungoverned actor.
    (when (empty? gov-holds)
      (throw (ex-info "refusing to write the console: the run produced no :governor-hold ledger fact"
                      {:ledger-facts (count ledger) :out out})))
    (when (empty? hard)
      (throw (ex-info (str "refusing to write the console: the run produced " (count gov-holds)
                           " :governor-hold fact(s) but none carried a governor violation "
                           "(rollout-phase gating only, no HARD compliance hold)")
                      {:governor-holds (count gov-holds) :out out})))
    (spit out (render result))
    (println "wrote" out)
    (println "  " (count runs) "graph runs,"
             (count ledger) "ledger facts,"
             (count (store/all-cases db)) "cases")
    (println "  " (count hard) "HARD governor holds across"
             (count rules) "distinct rules:" (str/join ", " (map nm rules)))
    (println "  " (count (store/dispatch-history db)) "check-in dispatches,"
             (count (store/closure-history db)) "case closures")))
