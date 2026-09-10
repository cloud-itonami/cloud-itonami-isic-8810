(ns care.facts
  "Per-jurisdiction community-care/adult-safeguarding regulatory
  catalog -- the G2-style spec-basis table the Safeguarding Governor
  checks every `:careplan/verify` proposal against ('did the advisor
  cite an OFFICIAL public source for this jurisdiction's community-
  care-coordination and adult-safeguarding framework, or did it invent
  one?').

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.

  Seed values are drawn from each jurisdiction's official long-term-
  care/adult-social-care authority and elder/vulnerable-adult
  safeguarding law (see `:provenance`); they are a STARTING catalog,
  not a from-scratch survey of all ~194 jurisdictions. Extending
  coverage is additive: add one map to `catalog`, cite a real source,
  done -- never invent a jurisdiction's requirements to make coverage
  look bigger.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the generic
  consent-record/care-plan-record/safeguarding-clearance-record/
  service-directory-verification-record evidence set every prior
  sibling's evidence checklist submits in some form; `:legal-basis` /
  `:owner-authority` / `:provenance` are the G2 citation the governor
  requires before any `:actuation/dispatch-checkin`/`:actuation/close-
  case` proposal can commit."
  {"JPN" {:name "Japan"
          :owner-authority "厚生労働省 (Ministry of Health, Labour and Welfare) -- 介護保険制度・地域包括ケア"
          :legal-basis "介護保険法 (Long-Term Care Insurance Act) / 高齢者虐待の防止、高齢者の養護者に対する支援等に関する法律 (Act on Prevention of Elder Abuse)"
          :national-spec "在宅介護コーディネーション事業者の登録要件および高齢者虐待防止基準"
          :provenance "https://www.mhlw.go.jp/stf/seisakunitsuite/bunya/hukushi_kaigo/kaigo_koureisha/index.html"
          :required-evidence ["同意記録 (consent-record)"
                              "ケアプラン記録 (care-plan-record)"
                              "セーフガーディング適格性記録 (safeguarding-clearance-record)"
                              "サービス提供者名簿確認記録 (service-directory-verification-record)"]}
   "USA" {:name "United States"
          :owner-authority "Administration for Community Living (ACL) / state Adult Protective Services (APS) agencies"
          :legal-basis "Older Americans Act, 42 U.S.C. §3001 et seq. / state Adult Protective Services statutes"
          :national-spec "Community-based care-coordination provider registration and elder/vulnerable-adult safeguarding requirements"
          :provenance "https://acl.gov/programs/protecting-rights-and-preventing-abuse"
          :required-evidence ["Consent record"
                              "Care-plan record"
                              "Safeguarding-clearance record"
                              "Service-directory verification record"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "Care Quality Commission (CQC) / local authority adult safeguarding boards"
          :legal-basis "Care Act 2014"
          :national-spec "Regulated community care-coordination provider and adult-safeguarding requirements"
          :provenance "https://www.cqc.org.uk/what-we-do/services-we-regulate/community-based-adult-social-care-services"
          :required-evidence ["Consent record"
                              "Care-plan record"
                              "Safeguarding-clearance record"
                              "Service-directory verification record"]}
   "DEU" {:name "Germany"
          :owner-authority "Bundesministerium für Gesundheit (BMG) / Betreuungsbehörden"
          :legal-basis "Elftes Buch Sozialgesetzbuch (SGB XI, Pflegeversicherung) / Betreuungsrecht (BGB §1896 ff.)"
          :national-spec "Registrierung ambulanter Pflegekoordinationsdienste und Erwachsenenschutzanforderungen"
          :provenance "https://www.bundesgesundheitsministerium.de/themen/pflege.html"
          :required-evidence ["Einwilligungsprotokoll (consent-record)"
                              "Pflegeplanprotokoll (care-plan-record)"
                              "Schutzkonzeptfreigabe (safeguarding-clearance-record)"
                              "Dienstleisterverzeichnisnachweis (service-directory-verification-record)"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to dispatch a
  check-in or close a case on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-8810 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `care.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
