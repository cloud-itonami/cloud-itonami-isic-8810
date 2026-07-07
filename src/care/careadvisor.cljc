(ns care.careadvisor
  "CareOps-LLM client -- the *contained intelligence node* for the care
  actor (README: \"Care Support Advisor\").

  It normalizes case-intake, drafts a per-jurisdiction community-care-
  coordination evidence checklist, screens cases for an unresolved
  safeguarding signal, drafts the check-in-dispatch action, and drafts
  the case-closure action. CRITICAL: it is a smart-but-untrusted
  advisor. It returns a *proposal* (with a rationale + the fields it
  cited), never a committed record or a real check-in dispatch/case
  closure. Every output is censored downstream by `care.governor`
  before anything touches the SSoT, and `:actuation/dispatch-checkin`/
  `:actuation/close-case` proposals NEVER auto-commit at any phase --
  see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation/dispatch-checkin | :actuation/close-case | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [care.facts :as facts]
            [care.registry :as registry]
            [care.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the case, jurisdiction or caregiver assignment. High
  confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "ケース記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :case/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- verify-careplan
  "Per-jurisdiction community-care-coordination evidence checklist
  draft. `:no-spec?` injects the failure mode we must defend against:
  proposing a checklist for a jurisdiction with NO official spec-basis
  in `care.facts` -- the Safeguarding Governor must reject this (never
  invent a jurisdiction's requirements)."
  [db {:keys [subject no-spec?]}]
  (let [c (store/case-of db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction c))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "care.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :careplan/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :careplan/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- screen-safeguarding
  "Safeguarding-signal screening draft. `:safeguarding-signal-
  unresolved?` on the case record injects the failure mode: the
  Safeguarding Governor must HOLD, un-overridably, on any unresolved
  signal."
  [db {:keys [subject]}]
  (let [c (store/case-of db subject)]
    (cond
      (nil? c)
      {:summary "対象ケース記録が見つかりません" :rationale "no case record"
       :cites [] :effect :safeguarding-screen/set :value {:case-id subject :verdict :unknown}
       :stake nil :confidence 0.0}

      (true? (:safeguarding-signal-unresolved? c))
      {:summary    (str (:recipient-name c) ": 未解決のセーフガーディング懸念信号を検出")
       :rationale  "スクリーニングが未解決のセーフガーディング懸念信号を検出。人手確認とホールドが必須。"
       :cites      [:safeguarding-check]
       :effect     :safeguarding-screen/set
       :value      {:case-id subject :verdict :unresolved}
       :stake      nil
       :confidence 0.95}

      :else
      {:summary    (str (:recipient-name c) ": 未解決のセーフガーディング懸念信号なし")
       :rationale  "セーフガーディングスクリーニング完了。"
       :cites      [:safeguarding-check]
       :effect     :safeguarding-screen/set
       :value      {:case-id subject :verdict :resolved}
       :stake      nil
       :confidence 0.9})))

(defn- propose-checkin-dispatch
  "Draft the actual CHECK-IN-DISPATCH action -- dispatching a real
  robot/caregiver in-home visit. ALWAYS `:stake :actuation/dispatch-
  checkin` -- this is a REAL-WORLD in-home act, never a draft the
  actor may auto-run. See README `Actuation`: no phase ever adds this
  op to a phase's `:auto` set (`care.phase`); the governor also always
  escalates on `:actuation/dispatch-checkin`. Two independent layers
  agree, deliberately."
  [db {:keys [subject]}]
  (let [c (store/case-of db subject)]
    {:summary    (str subject " 向けチェックイン派遣提案"
                      (when c (str " (recipient=" (:recipient-name c) ")")))
     :rationale  (if c
                   (str "caregiver-current-caseload=" (:caregiver-current-caseload c)
                        " caregiver-max-caseload=" (:caregiver-max-caseload c))
                   "ケース記録が見つかりません")
     :cites      (if c [subject] [])
     :effect     :case/mark-dispatched
     :value      {:case-id subject}
     :stake      :actuation/dispatch-checkin
     :confidence (if (and c (not (registry/caregiver-workload-exceeds-maximum? c))) 0.9 0.3)}))

(defn- propose-case-closure
  "Draft the actual CASE-CLOSURE action -- discharging/closing a real
  case. ALWAYS `:stake :actuation/close-case` -- this is a REAL-WORLD
  discharge act, never a draft the actor may auto-run. See README
  `Actuation`: no phase ever adds this op to a phase's `:auto` set
  (`care.phase`); the governor also always escalates on `:actuation/
  close-case`. Two independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [c (store/case-of db subject)]
    {:summary    (str subject " 向けケースクローズ提案"
                      (when c (str " (recipient=" (:recipient-name c) ")")))
     :rationale  (if c
                   (str "safeguarding-signal-unresolved?=" (:safeguarding-signal-unresolved? c))
                   "ケース記録が見つかりません")
     :cites      (if c [subject] [])
     :effect     :case/mark-closed
     :value      {:case-id subject}
     :stake      :actuation/close-case
     :confidence (if (and c (not (:safeguarding-signal-unresolved? c))) 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :case/intake                 (normalize-intake db request)
    :careplan/verify             (verify-careplan db request)
    :safeguarding/screen         (screen-safeguarding db request)
    :actuation/dispatch-checkin  (propose-checkin-dispatch db request)
    :actuation/close-case        (propose-case-closure db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは地域包括ケア・コーディネーション事業のチェックイン派遣・ケースクローズ"
       "エージェントの助言者です。与えられた事実のみに基づき、提案を1つだけEDNマップで"
       "返します。説明や前置きは一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:case/upsert|:careplan/set|:safeguarding-screen/set|"
       ":case/mark-dispatched|:case/mark-closed) "
       ":stake(:actuation/dispatch-checkin か :actuation/close-case か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :careplan/verify             {:case (store/case-of st subject)}
    :safeguarding/screen         {:case (store/case-of st subject)}
    :actuation/dispatch-checkin  {:case (store/case-of st subject)}
    :actuation/close-case        {:case (store/case-of st subject)}
    {:case (store/case-of st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Safeguarding Governor
  escalates/holds -- an LLM hiccup can never auto-dispatch a check-in
  or auto-close a case."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :careadvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
