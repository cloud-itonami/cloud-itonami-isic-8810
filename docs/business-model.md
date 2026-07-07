# Business Model: Community Care Coordination

## Classification

- Repository: `cloud-itonami-isic-8810`
- ISIC Rev.5: `8810`
- Activity: social work activities without accommodation for older persons and
  persons with disabilities
- Social impact: aging-in-place, disability support, caregiver relief

## Customer

- local governments
- community care providers
- NPOs
- caregiver cooperatives
- clinics with social-support referrals

## Offer

- consented care-plan records
- check-in and task workflow
- caregiver and service matching
- benefits navigation
- safeguarding escalation
- outcome and workload reporting

## Revenue

- program setup fee
- monthly case-management subscription
- per-care-team support fee
- reporting and grant-compliance package
- operator training and certification

## Trust Controls

- consent and purpose limitation
- safeguarding signals escalate
- eligibility/discharge decisions require human review
- caregiver workload and visit records are auditable
- a fabricated jurisdiction citation, incomplete evidence, an overloaded
  caregiver's own workload, or an unresolved safeguarding signal -- each
  forces a hold, not an override
- check-in dispatch and case closure are logged and escalated, and neither
  can be finalized twice for the same case: a double-dispatch or double-
  closure attempt is held off this actor's own case facts alone, with no
  upstream comparison needed

## Safeguarding Governor: decision rule

`blueprint.edn` fixes `:itonami.blueprint/governor` to `:safeguarding-governor` —
this is not a generic "review step," it is the one gate every proposed action
in this business must pass before a caregiver or the check-in/reminder robot
(`:itonami.blueprint/robotics true`) is allowed to act. The governor sits
between the Care Support Advisor and execution, per the README's Core
Contract:

```text
Care Support Advisor -> Safeguarding Governor -> task, hold, or escalate
```

**Approves** ("task"): routine support actions proposed against a case that
already has a consented care-plan record on file, no open safeguarding
signal, and no eligibility/discharge decision attached — e.g. a scheduled
check-in visit, a reminder, a caregiver/service match, a benefits-navigation
step. These proceed straight to the caregiver queue / support ledger.

**Rejects or escalates** ("hold, or escalate"): the governor refuses to let
the advisor (or the robot it directs) do any of the following on its own
authority —

- hide or suppress a safeguarding signal surfaced during a check-in
- override or bypass consent / purpose limitation
- make an eligibility or discharge decision without a human reviewer
- let the robot perform a `:high`/`:safety-critical` action — i.e. operating
  near an elder, a care recipient, or inside a home — without human sign-off
  (README, "Robotics premise")

**Why this rule, specifically**: it is grounded directly in this blueprint's
`:itonami.blueprint/social-impact` tags — `:aging-in-place`,
`:disability-support`, `:caregiver-relief`. The population this business
touches (older persons and persons with disabilities, in their own homes,
via ISIC 8810 "social work activities without accommodation") is exactly the
population where an unreviewed discharge, a suppressed neglect signal, or an
unsupervised robot action in a private home carries the highest harm. The
single invariant is therefore: *the advisor can coordinate support tasks, but
can never itself close the loop on safeguarding, consent, or
eligibility/discharge* — that authority stays with a human reviewer, every
time, no standing exemption.

This exact rule is what the companion playable prototype
(`network-isekai` — see [Operator Guide](operator-guide.md)) turns into a
mechanic: you must brief at the "office" depot for this round's
safeguarding-governor case sign-off before a case can be closed; closing a
case without that sign-off is scored as an unsafeguarded case — a governor
violation, not just a missed step.

## Required Technologies

`blueprint.edn` declares `:itonami.blueprint/required-technologies
[:robotics :identity :forms :dmn :bpmn :audit-ledger :optimization]` and
`:itonami.blueprint/optional-technologies [:telemetry]`. Each item is tied to
a concrete artifact of this business, not a generic catalog entry:

- **robotics** — drives the check-in/reminder/assistive-support robot itself
  (README "Robotics premise"): the physical-domain worker for aging-in-place
  and disability-support visits, gated by the Safeguarding Governor before
  any `:high`/`:safety-critical` in-home action.
- **identity** — backs the consent record and purpose limitation control
  (Trust Controls) tying every care-plan, check-in, and caregiver record to a
  specific consenting care recipient, caregiver, or NPO/clinic referrer.
- **forms** — captures the person-centered care plan, intake, and
  benefits-navigation data that the Offer promises ("consented care-plan
  records," "benefits navigation").
- **dmn** — encodes the Safeguarding Governor's own approve/hold/escalate
  decision table described above: the rules for when a safeguarding signal,
  consent state, or eligibility/discharge flag forces an escalation instead
  of letting a task proceed.
- **bpmn** — runs the check-in and task workflow and the caregiver/service
  matching process (Offer: "check-in and task workflow," "caregiver and
  service matching") — the intake → propose → approve → execute → audit loop
  documented in the [Operator Guide](operator-guide.md).
- **audit-ledger** — backs the support ledger and caregiver queue (README
  Core Contract) and makes caregiver workload and visit records auditable
  (Trust Controls), feeding the outcome and workload reporting revenue line.
- **optimization** — balances caregiver/service matching against each
  caregiver's own recorded maximum caseload (Trust Controls: "caregiver
  workload and visit records are auditable"), the same ceiling the
  Safeguarding Governor independently re-checks before any check-in
  dispatch.

**Optional**: **telemetry** — carries the check-in signals (from the robot
or a caregiver's visit) that trigger the safeguarding escalation path before
the governor ever sees them, where a site has sensor/wearable integration.
