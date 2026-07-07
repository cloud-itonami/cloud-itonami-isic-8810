# ADR-0001: Care Support Advisor ⊣ Safeguarding Governor architecture

## Status

Accepted. `cloud-itonami-isic-8810` promoted from `:blueprint` to
`:implemented` in the `kotoba-lang/industry` registry.

## Context

`cloud-itonami-isic-8810` publishes an OSS business blueprint for
community care coordination: check-ins, benefits navigation, caregiver
scheduling and non-residential support operations for older persons
and persons with disabilities, run by a qualified operator so a
community keeps its own case, consent and audit records instead of
renting a closed SaaS. Like every prior actor in this fleet, the
blueprint alone is not an implementation: this ADR records the
governed-actor architecture that promotes it to real, tested code,
following the same langgraph-clj StateGraph + independent Governor +
Phase 0→3 rollout pattern established by `cloud-itonami-isic-6511`
(life insurance) and applied across forty-nine prior siblings, most
recently `cloud-itonami-isic-3512` (community renewable energy).

## Decision

### Decision 1: entity and op shape

The primary entity is a `case` (a community-care-coordination case for
a care recipient, matching the blueprint's own operator-guide language
-- "Case 014... Mr. Sato"). Five ops: `:case/intake` (directory upsert,
no capital risk), `:careplan/verify` (per-jurisdiction community-care-
coordination evidence checklist, never auto), `:safeguarding/screen`
(safeguarding-signal screening, unconditional-evaluation discipline,
never auto), `:actuation/dispatch-checkin` (POSITIVE, high-stakes --
dispatching the real robot/caregiver in-home check-in visit), and
`:actuation/close-case` (POSITIVE, high-stakes -- discharging/closing
a real case). This matches the dual-actuation-on-one-entity shape
every recent dual-actuation sibling uses, grounded directly in this
blueprint's own published operator-guide walkthrough: "Execute" (the
robot or caregiver performs the check-in) and "closing a case" (an
eligibility/discharge decision requiring human review, per the Trust
Controls and the operator-guide's explicit "violation case" framing).

### Decision 2: `caregiver-workload-exceeds-maximum?` -- the 5th MAXIMUM-ceiling check

Following `facility.registry/occupancy-exceeds-capacity?` (1st),
`school.registry/class-size-exceeds-maximum?` (2nd), `card.registry/
settlement-amount-exceeds-authorized?` (3rd) and `recovery.registry/
contamination-percentage-exceeds-maximum?` (4th), `care.registry/
caregiver-workload-exceeds-maximum?` applies the SAME ceiling-only
comparison to a case's own assigned caregiver's own recorded current
caseload against their own recorded maximum caseload -- a direct,
natural mapping onto real caregiver-workload/burnout-prevention
practice (dispatching an already-overloaded caregiver for one more
in-home visit is exactly the failure mode a community-care operator's
own Trust Controls name: "caregiver workload and visit records are
auditable"). Gates only `:actuation/dispatch-checkin`.

### Decision 3: `safeguarding-signal-unresolved-violations` -- the 34th unconditional-evaluation screening grounding, and the SECOND "safeguarding" grounding specifically

Before writing this check, `congregation.governor` was read directly
(not just grepped) to distinguish it correctly: `congregation.
governor/safeguarding-concern-unresolved-violations` (ADR-2607081200,
the 31st grounding overall) verifies whether a MATTER carries an
unresolved allegation/risk flag against a person within the
congregation. This actor's `safeguarding-signal-unresolved-violations`
is a related but textually and semantically distinct shape: the
signal is surfaced by THIS blueprint's own check-in visit itself (e.g.
a missed-medication report escalating to a neglect concern, per the
operator-guide's own Mr. Sato walkthrough), not an allegation against
a person. Named `safeguarding-signal-unresolved` (not `-concern-
unresolved`) specifically to keep the two grounded shapes textually
distinguishable in ledger/test output. It reuses the unconditional-
evaluation DISCIPLINE (`casualty.governor/sanctions-violations`'s
original fix) for the 34th distinct application overall, continuing
the count established across this fleet's builds (water=25th,
telecom=26th, aerospace=27th, recovery=28th, consulting=29th,
union=30th, congregation=31st, fab=32nd, energy=33rd, care=34th).
Gates `:safeguarding/screen` and `:actuation/close-case` specifically
-- matching this blueprint's own Trust Control that "eligibility/
discharge decisions require human review" and the governor may never
"hide or suppress a safeguarding signal surfaced during a check-in."

### Decision 4: dedicated double-actuation-guard booleans

`:checkin-dispatched?`/`:case-closed?` are dedicated booleans on the
`case` record, never a single `:status` value -- the same discipline
every prior sibling governor's guards establish, informed by
`cloud-itonami-isic-6492`'s real status-lifecycle bug
(ADR-2607071320).

### Decision 5: Store protocol, MemStore + DatomicStore parity, and a naming note

`care.store/Store` is implemented by both `MemStore` (atom-backed,
default for dev/tests/demo) and `DatomicStore` (`langchain.db`-
backed), proven to satisfy the same contract in `test/care/
store_contract_test.clj` -- the same seam every sibling actor uses so
swapping the SSoT backend is a configuration change, not a rewrite.
The protocol's per-entity accessor is named `case-of`, not `case` --
`case` is a Clojure special form, and every backend's `commit-record!`
dispatches on `effect` via `(case effect ...)`, so the entity accessor
must not shadow it (discovered and fixed before any test was run, not
a later patch).

### Decision 6: Phase 0→3 rollout

Phase 3's `:auto` set has exactly one member, `:case/intake` (no
capital risk). `:careplan/verify` and `:safeguarding/screen` are never
auto-eligible at any phase (matching every sibling's screening-op
posture), and `:actuation/dispatch-checkin`/`:actuation/close-case`
are permanently excluded from every phase's `:auto` set -- a
structural fact, not a rollout milestone, enforced by BOTH `care.
phase` and `care.governor`'s `high-stakes` set independently.

### Decision 7: no bespoke domain capability lib

This vertical's case records are practice-specific rather than a
shared cross-operator data contract, so `care.*` runs on the generic
robotics/identity/forms/dmn/bpmn/audit-ledger/optimization stack only
-- the same posture `9412`/`8720`/`8521`/`3030`/`3830`/`7020`/`9420`/
`9491`/`3512` and others without a bespoke capability lib already
establish.

### Decision 8: mock + LLM advisor pair

`care.careadvisor` provides `mock-advisor` (deterministic, default
everywhere -- the actor graph and governor contract run offline) and
`llm-advisor` (backed by `langchain.model/ChatModel`, with a defensive
EDN-proposal parser so a malformed LLM response degrades to a safe
low-confidence noop rather than ever auto-dispatching a check-in or
auto-closing a case).

### Decision 9: blueprint.edn field-sync fixes

Two stale-scaffold inconsistencies in `blueprint.edn`, discovered
during the standard "survey blueprint scaffold" step before writing
any code, were fixed as part of this promotion (the same class of fix
`card.6619`'s, `water.3600`'s, `telecom.6190`'s, `aerospace.3030`'s,
`fab.2610`'s and `energy.3512`'s own ADR-0001s document):

1. `:itonami.blueprint/id` was the stale pre-rename value
   `"cloud-itonami-8810"` (missing `isic-`), while the repo folder,
   README title and this actor's own `:business-id` already use the
   corrected `cloud-itonami-isic-8810`. Fixed to match.
2. `:itonami.blueprint/required-technologies`/`:optional-technologies`
   were missing entirely despite `docs/business-model.md`'s own prior
   text already describing (informally, before this fix) the same
   technology set the `kotoba-lang/industry` registry's own entry for
   `"8810"` states: `[:robotics :identity :forms :dmn :bpmn :audit-
   ledger :optimization]` / `[:telemetry]`. Fixed to match the
   registry exactly, and `docs/business-model.md`'s own stale
   "blueprint.edn does not yet populate this field" caveat paragraph
   was removed and replaced with the now-accurate field values.

## Alternatives considered

- **Reusing `congregation.governor/safeguarding-concern-unresolved-
  violations`'s exact name for this actor's check.** Rejected: the two
  concepts are related (both are unconditionally-evaluated unresolved-
  flag checks) but not identical (a matter-level allegation vs. a
  check-in-surfaced signal); reusing the identical name would have
  made a real, if related, distinct grounding look like copy-paste
  reuse of the same concept, which the fleet's precedent-verification
  discipline (established by `leasing`'s ADR-0001, reinforced by
  `union`'s, `congregation`'s and `fab`'s) specifically warns against
  claiming without checking.
- **A single "case-safety" check merging caregiver-workload and
  safeguarding-signal concerns.** Rejected: caregiver workload is a
  ground-truth numeric recompute needing no proposal inspection;
  safeguarding-signal status is an unconditionally-evaluated flag that
  must also HARD-hold the screening op itself on its own finding --
  merging them would lose the screening op's self-hold property, the
  same reasoning `fab`'s and `energy`'s ADR-0001s document for their
  own analogous ground-truth/unconditional-flag distinctions.
- **Naming the protocol accessor `case` to match the entity name
  exactly (as `congregation.store/matter`, `school.store/student` do
  for their own entities).** Rejected once discovered: `case` collides
  with Clojure's `case` special form, which every backend's `commit-
  record!` already uses for effect dispatch -- renamed to `case-of`
  before any code depending on it was written.

## Consequences

- Fiftieth actor in this fleet (49 implemented before this build).
- Confirms the MAXIMUM-ceiling check family generalizes to a fifth,
  genuinely distinct domain (caregiver-workload safety).
- Establishes the SECOND grounding of the "safeguarding" unconditional-
  evaluation shape, correctly distinguished (not conflated) from
  `congregation`'s matter-level concept via direct source reading, not
  just a name grep.
- `MemStore` ‖ `DatomicStore` parity is proven by `test/care/
  store_contract_test.clj`, the same `:db-api`-driven swap pattern
  every sibling actor uses.
- Two pre-existing `blueprint.edn` inconsistencies (stale ID, missing
  required/optional-technologies fields) fixed as in-scope minor
  consistency work, along with a stale caveat paragraph in
  `docs/business-model.md`.
