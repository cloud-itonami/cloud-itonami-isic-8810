(ns care.governor
  "Safeguarding Governor -- the independent compliance layer that earns
  the CareOps-LLM the right to commit. The LLM has no notion of
  community-care-coordination regulatory law, whether the assigned
  caregiver's own workload actually stays within their own recorded
  maximum caseload, whether a safeguarding signal against a case has
  actually stayed unresolved, or when an act stops being a draft and
  becomes a real-world in-home check-in dispatch or case closure, so
  this MUST be a separate system able to *reject* a proposal and fall
  back to HOLD -- the care analog of `cloud-itonami-isic-6512`'s
  CasualtyGovernor.

  Six checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them (you don't get to approve your way past a
  fabricated jurisdiction spec-basis, incomplete evidence, an
  overloaded caregiver's own workload, an unresolved safeguarding
  signal, or a double dispatch/closure). The confidence/actuation gate
  is SOFT: it asks a human to look (low confidence / actuation), and
  the human may approve -- but see `care.phase`: for `:stake
  :actuation/dispatch-checkin`/`:actuation/close-case` (a real in-home
  act or a real discharge decision) NO phase ever allows auto-commit
  either. Two independent layers agree that actuation is always a
  human call.

    1. Spec-basis                  -- did the care-plan proposal cite
                                       an OFFICIAL source (`care.
                                       facts`), or invent one?
    2. Evidence incomplete         -- for `:actuation/dispatch-
                                       checkin`/`:actuation/close-
                                       case`, has the case actually
                                       been assessed with a full
                                       consent-record/care-plan-
                                       record/safeguarding-clearance-
                                       record/service-directory-
                                       verification-record evidence
                                       checklist on file?
    3. Caregiver workload exceeds
       maximum                        -- for `:actuation/dispatch-
                                       checkin`, INDEPENDENTLY
                                       recompute whether the case's own
                                       assigned caregiver's own
                                       recorded current caseload
                                       exceeds their own recorded
                                       maximum caseload (`care.
                                       registry/caregiver-workload-
                                       exceeds-maximum?`) -- needs no
                                       proposal inspection at all. The
                                       FIFTH instance of this fleet's
                                       MAXIMUM-ceiling check family
                                       (`facility.governor/occupancy-
                                       exceeds-capacity-violations`/
                                       `school.governor/class-size-
                                       exceeds-maximum-violations`/
                                       `card.governor/settlement-
                                       amount-exceeds-authorized-
                                       violations`/`recovery.governor/
                                       contamination-percentage-
                                       exceeds-maximum-violations`
                                       established the first four).
    4. Safeguarding signal
       unresolved                     -- reported by THIS proposal
                                       itself (a `:safeguarding/
                                       screen` that just found one), or
                                       already on file for the case
                                       (`:safeguarding/screen`/
                                       `:actuation/close-case`).
                                       Evaluated UNCONDITIONALLY (not
                                       scoped to a specific op), the
                                       SAME discipline `casualty.
                                       governor/sanctions-violations`/
                                       ...(thirty-three prior siblings,
                                       most recently `energy.governor/
                                       grid-instability-flag-
                                       unresolved-violations`)...
                                       established -- the THIRTY-
                                       FOURTH distinct application of
                                       this exact discipline overall,
                                       and the SECOND application
                                       SPECIFICALLY grounded in a
                                       'safeguarding concern/signal'
                                       shape (`congregation.governor/
                                       safeguarding-concern-unresolved-
                                       violations` established the
                                       first, at a MATTER level --
                                       a pending allegation against a
                                       congregation). Distinct from
                                       that sibling: this signal is
                                       surfaced by THIS blueprint's own
                                       check-in visit (e.g. a missed-
                                       medication report escalating to
                                       a neglect concern, per the
                                       operator-guide's own Mr. Sato
                                       walkthrough), not an allegation
                                       against a person -- named
                                       `safeguarding-signal-unresolved`
                                       (not `-concern-unresolved`) to
                                       keep the two grounded shapes
                                       textually distinguishable.
                                       Exercised in tests/demo via
                                       `:safeguarding/screen` DIRECTLY,
                                       not via the actuation op against
                                       an unscreened case -- see this
                                       ns's own test suite.
    5. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:actuation/
                                       dispatch-checkin`/`:actuation/
                                       close-case` (REAL in-home/
                                       discharge acts) -> escalate.

  Two more guards, double-dispatch/double-closure prevention, are
  enforced but NOT listed as numbered HARD checks above because they
  need no upstream comparison at all -- `already-dispatched-
  violations`/`already-closed-violations` refuse to dispatch a check-
  in/close a case for the SAME case twice, off dedicated `:checkin-
  dispatched?`/`:case-closed?` facts (never a `:status` value) -- the
  SAME 'check a dedicated boolean, not status' discipline every prior
  sibling governor's guards establish, informed by `cloud-itonami-
  isic-6492`'s status-lifecycle bug (ADR-2607071320)."
  (:require [care.facts :as facts]
            [care.registry :as registry]
            [care.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Dispatching a real in-home check-in and closing a real case are the
  two real-world actuation events this actor performs -- a two-member
  set, matching every prior dual-actuation sibling's shape. Both are
  POSITIVE actuations (dispatching/finalizing a record), matching this
  fleet's majority actuation shape (3600/6190 remain the only
  negative-actuation exceptions)."
  #{:actuation/dispatch-checkin :actuation/close-case})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:careplan/verify` (or actuation) proposal with no spec-basis
  citation is a HARD violation -- never invent a jurisdiction's
  community-care-coordination requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:careplan/verify :actuation/dispatch-checkin :actuation/close-case} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は地域包括ケア運営基準として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:actuation/dispatch-checkin`/`:actuation/close-case`, the
  jurisdiction's required consent-record/care-plan-record/
  safeguarding-clearance-record/service-directory-verification-record
  evidence must actually be satisfied -- do not trust the advisor's
  self-reported confidence alone."
  [{:keys [op subject]} st]
  (when (contains? #{:actuation/dispatch-checkin :actuation/close-case} op)
    (let [c (store/case-of st subject)
          careplan (store/careplan-of st subject)]
      (when-not (and careplan
                     (facts/required-evidence-satisfied?
                      (:jurisdiction c) (:checklist careplan)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(同意記録/ケアプラン記録/セーフガーディング適格性記録/サービス提供者名簿確認記録等)が充足していない状態での提案"}]))))

(defn- caregiver-workload-exceeds-maximum-violations
  "For `:actuation/dispatch-checkin`, INDEPENDENTLY recompute whether
  the case's own assigned caregiver's own current caseload exceeds
  their own maximum caseload via `care.registry/caregiver-workload-
  exceeds-maximum?` -- needs no proposal inspection at all, since its
  inputs are permanent ground-truth fields already on the case."
  [{:keys [op subject]} st]
  (when (= op :actuation/dispatch-checkin)
    (let [c (store/case-of st subject)]
      (when (registry/caregiver-workload-exceeds-maximum? c)
        [{:rule :caregiver-workload-exceeds-maximum
          :detail (str subject " の担当介護者の現在ケース数(" (:caregiver-current-caseload c)
                      ")が上限(" (:caregiver-max-caseload c) ")を超過")}]))))

(defn- safeguarding-signal-unresolved-violations
  "An unresolved safeguarding signal -- reported by THIS proposal (e.g.
  a `:safeguarding/screen` that itself just found one), or already on
  file in the store for the case (`:safeguarding/screen`/`:actuation/
  close-case`) -- is a HARD, un-overridable hold. Evaluated
  UNCONDITIONALLY (not scoped to a specific op) so the screening op
  itself can HARD-hold on its own finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (= :unresolved (get-in proposal [:value :verdict]))
        case-id (when (contains? #{:safeguarding/screen :actuation/close-case} op) subject)
        hit-on-file? (and case-id (= :unresolved (:verdict (store/safeguarding-screen-of st case-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :safeguarding-signal-unresolved
        :detail "未解決のセーフガーディング懸念信号がある状態でのケースクローズ提案は進められない"}])))

(defn- already-dispatched-violations
  "For `:actuation/dispatch-checkin`, refuses to dispatch a check-in
  for the SAME case twice, off a dedicated `:checkin-dispatched?` fact
  (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/dispatch-checkin)
    (when (store/case-already-dispatched? st subject)
      [{:rule :already-dispatched
        :detail (str subject " は既にチェックイン派遣済み")}])))

(defn- already-closed-violations
  "For `:actuation/close-case`, refuses to close the SAME case twice,
  off a dedicated `:case-closed?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/close-case)
    (when (store/case-already-closed? st subject)
      [{:rule :already-closed
        :detail (str subject " は既にケースクローズ済み")}])))

(defn check
  "Censors a CareOps-LLM proposal against the governor rules. Returns
  {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (caregiver-workload-exceeds-maximum-violations request st)
                           (safeguarding-signal-unresolved-violations request proposal st)
                           (already-dispatched-violations request st)
                           (already-closed-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
