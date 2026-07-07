# cloud-itonami-isic-8810

Open Business Blueprint for **ISIC Rev.5 8810**: social work
activities without accommodation for older persons and persons with
disabilities.

This repository publishes a community-care-coordination actor -- case
intake, community-care-coordination regulatory assessment,
safeguarding-signal screening, check-in-visit dispatch and case
closure -- as an OSS business that any qualified care-coordination
operator can fork, deploy, run, improve and sell, so a community or
independent provider never surrenders care-recipient data and ledgers
to a closed SaaS.

Built on this workspace's
[`langgraph-clj`](https://github.com/com-junkawasaki/langgraph-clj)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet
([`cloud-itonami-isic-6511`](https://github.com/cloud-itonami/cloud-itonami-isic-6511),
[`6512`](https://github.com/cloud-itonami/cloud-itonami-isic-6512),
[`6621`](https://github.com/cloud-itonami/cloud-itonami-isic-6621),
[`6622`](https://github.com/cloud-itonami/cloud-itonami-isic-6622),
[`6629`](https://github.com/cloud-itonami/cloud-itonami-isic-6629),
[`6520`](https://github.com/cloud-itonami/cloud-itonami-isic-6520),
[`6530`](https://github.com/cloud-itonami/cloud-itonami-isic-6530),
[`6820`](https://github.com/cloud-itonami/cloud-itonami-isic-6820),
[`6612`](https://github.com/cloud-itonami/cloud-itonami-isic-6612),
[`6492`](https://github.com/cloud-itonami/cloud-itonami-isic-6492),
[`6920`](https://github.com/cloud-itonami/cloud-itonami-isic-6920),
[`6611`](https://github.com/cloud-itonami/cloud-itonami-isic-6611),
[`7120`](https://github.com/cloud-itonami/cloud-itonami-isic-7120),
[`8620`](https://github.com/cloud-itonami/cloud-itonami-isic-8620),
[`8530`](https://github.com/cloud-itonami/cloud-itonami-isic-8530),
[`9200`](https://github.com/cloud-itonami/cloud-itonami-isic-9200),
[`7500`](https://github.com/cloud-itonami/cloud-itonami-isic-7500),
[`9603`](https://github.com/cloud-itonami/cloud-itonami-isic-9603),
[`9521`](https://github.com/cloud-itonami/cloud-itonami-isic-9521),
[`9321`](https://github.com/cloud-itonami/cloud-itonami-isic-9321),
[`8730`](https://github.com/cloud-itonami/cloud-itonami-isic-8730),
[`9102`](https://github.com/cloud-itonami/cloud-itonami-isic-9102),
[`9103`](https://github.com/cloud-itonami/cloud-itonami-isic-9103),
[`9602`](https://github.com/cloud-itonami/cloud-itonami-isic-9602),
[`9000`](https://github.com/cloud-itonami/cloud-itonami-isic-9000),
[`8890`](https://github.com/cloud-itonami/cloud-itonami-isic-8890),
[`8610`](https://github.com/cloud-itonami/cloud-itonami-isic-8610),
[`9311`](https://github.com/cloud-itonami/cloud-itonami-isic-9311),
[`8510`](https://github.com/cloud-itonami/cloud-itonami-isic-8510),
[`9412`](https://github.com/cloud-itonami/cloud-itonami-isic-9412),
[`6491`](https://github.com/cloud-itonami/cloud-itonami-isic-6491),
[`8720`](https://github.com/cloud-itonami/cloud-itonami-isic-8720),
[`8521`](https://github.com/cloud-itonami/cloud-itonami-isic-8521),
[`6619`](https://github.com/cloud-itonami/cloud-itonami-isic-6619),
[`3600`](https://github.com/cloud-itonami/cloud-itonami-isic-3600),
[`6190`](https://github.com/cloud-itonami/cloud-itonami-isic-6190),
[`3030`](https://github.com/cloud-itonami/cloud-itonami-isic-3030),
[`3830`](https://github.com/cloud-itonami/cloud-itonami-isic-3830),
[`7020`](https://github.com/cloud-itonami/cloud-itonami-isic-7020),
[`9420`](https://github.com/cloud-itonami/cloud-itonami-isic-9420),
[`9491`](https://github.com/cloud-itonami/cloud-itonami-isic-9491),
[`2610`](https://github.com/cloud-itonami/cloud-itonami-isic-2610),
[`3512`](https://github.com/cloud-itonami/cloud-itonami-isic-3512)) --
here it is **Care Support Advisor ⊣ Safeguarding Governor**.

> **Why an actor layer at all?** An LLM is great at drafting a case-
> intake summary, normalizing records, and checking whether an
> assigned caregiver's own recorded current caseload actually stays
> within their own recorded maximum caseload -- but it has **no notion
> of which jurisdiction's community-care-coordination and adult-
> safeguarding law is official, no license to dispatch a real robot/
> caregiver in-home check-in or close a real case, and no way to know
> on its own whether a safeguarding signal against a case has actually
> stayed unresolved**. Letting it dispatch a check-in or close a case
> directly invites fabricated regulatory citations, an overloaded
> caregiver being sent on one more in-home visit, and an unresolved
> safeguarding signal being quietly overlooked -- and liability, and
> care-recipient-safety risk, for whoever runs it. This project seals
> the CareOps-LLM into a single node and wraps it with an independent
> **Safeguarding Governor**, a human **approval workflow**, and an
> immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers case intake through community-care-coordination
regulatory assessment, safeguarding-signal screening, check-in-visit
dispatch and case closure. It does **not**, by itself, hold any
registration required to operate as a community-care-coordination
provider in a given jurisdiction, and it does not claim to. It also
does not model a real care-coordination system, the actual in-home
visit itself, or clinical/social-work judgment -- `care.registry/
caregiver-workload-exceeds-maximum?` is a pure ceiling recompute
against the case's own recorded fields, not a clinical assessment.
Whoever deploys and operates a live instance (a registered community-
care-coordination provider) supplies any jurisdiction-specific
registration, the real caregiver workforce and the real care-
coordination-system integrations, and bears that jurisdiction's
liability -- the software supplies the governed, spec-cited, audited
execution scaffold so that provider does not have to build the
compliance layer from scratch.

### Actuation

**Dispatching a real robot/caregiver in-home check-in or closing a
real case is never autonomous, at any phase, by construction.** Two
independent layers enforce this (`care.governor`'s `:actuation/
dispatch-checkin`/`:actuation/close-case` high-stakes gate and `care.
phase`'s phase table, which never puts `:actuation/dispatch-checkin`/
`:actuation/close-case` in any phase's `:auto` set) -- see `care.
phase`'s docstring and `test/care/phase_test.clj`'s `dispatch-checkin-
never-auto-at-any-phase`/`close-case-never-auto-at-any-phase`. The
actor may draft, check and recommend; a human care-coordinator/
clinical lead is always the one who actually dispatches a check-in or
closes a case. Like `6512`/`6622`/`6520`/`6530`/`6820`/`6920`/`6611`/
`8530`/`9200`/`9521`/`8730`/`9102`/`9103`/`8890`/`8610`/`8510`/`9412`/
`8720`/`8521`/`6619`/`3600`/`6190`/`3030`/`3830`/`9420`/`9491`/`2610`/
`3512`, this actor has TWO actuation events, both POSITIVE (dispatching/
finalizing a real record), matching the majority pattern in this fleet
(`3600`/`6190` are the fleet's two NEGATIVE-actuation exceptions).

## The core contract

```
case intake + jurisdiction facts (care.facts, spec-cited)
        |
        v
   ┌──────────────┐   proposal      ┌───────────────────────┐
   │ Care Support │ ─────────────▶ │ Safeguarding                  │  (independent system)
   │ Advisor      │  + citations    │ Governor:                    │
   │ (sealed)     │                 │ spec-basis · evidence-       │
   └──────────────┘         commit ◀────┼──────────▶ hold │ incomplete ·
                                 │             │           │ caregiver-workload-
                           record + ledger  escalate ─▶ human   exceeds-maximum
                                             (ALWAYS for         (ceiling) ·
                                              :actuation/dispatch-       safeguarding-signal-
                                              checkin /                  unresolved
                                              :actuation/close-case)     (unconditional) ·
                                                                          already-dispatched/
                                                                          -closed
```

**The CareOps-LLM never dispatches a check-in or closes a case the
Safeguarding Governor would reject, and never does so without a human
sign-off.** Hard violations (fabricated regulatory requirements;
unsupported evidence; an overloaded caregiver's own workload; an
unresolved safeguarding signal; a double dispatch or closure) force
**hold** and *cannot* be approved past; a clean dispatch/closure
proposal still always routes to a human.

## Run

```bash
clojure -M:dev:run     # walk one clean dual-actuation lifecycle + five HARD-hold cases through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here a care-coordination robot
performs check-in, reminders and assistive support for care recipients
under the actor, gated by the independent **Safeguarding Governor**.
The governor never dispatches hardware itself; `:high`/`:safety-
critical` actions (such as operating near elders, care recipients or
in homes) require human sign-off.

A live sample of the operator console (robotics safety console, shared
template) is rendered in
[docs/samples/operator-console.html](docs/samples/operator-console.html)
-- pure-data HTML output of `kotoba.robotics.ui`.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Safeguarding Governor, check-in-dispatch + case-closure draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`8810`). This vertical's case records are practice-specific rather
than a shared cross-operator data contract, so `care.*` runs on the
generic robotics/identity/forms/dmn/bpmn/audit-ledger/optimization
stack only -- no bespoke domain capability lib to reference at all.

## Layout

| File | Role |
|---|---|
| `src/care/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + separate check-in-dispatch/case-closure history. No dynamically-filed sub-record -- both actuation ops act directly on a pre-seeded case, and the double-actuation guards check dedicated `:checkin-dispatched?`/`:case-closed?` booleans rather than a `:status` value |
| `src/care/registry.cljc` | Check-in-dispatch + case-closure draft records, plus `caregiver-workload-exceeds-maximum?` -- the FIFTH instance of this fleet's MAXIMUM-ceiling check family (`facility`/`school`/`card`/`recovery` established the first four) |
| `src/care/facts.cljc` | Per-jurisdiction community-care-coordination/adult-safeguarding catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/care/careadvisor.cljc` | **CareOps-LLM** -- `mock-advisor` ‖ `llm-advisor`; intake/careplan-verification/safeguarding-screening/check-in-dispatch/case-closure proposals |
| `src/care/governor.cljc` | **Safeguarding Governor** -- 4 HARD checks (spec-basis · evidence-incomplete · caregiver-workload-exceeds-maximum, pure ground-truth ceiling recompute · safeguarding-signal-unresolved, unconditional evaluation, the THIRTY-FOURTH grounding of this discipline, the SECOND specifically "safeguarding"-shaped one after `congregation`'s matter-level concept) + already-dispatched/already-closed guards + 1 soft (confidence/actuation gate) |
| `src/care/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted verify → supervised (both check-in dispatch and case closure always human; case intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/care/operation.cljc` | **OperationActor** -- langgraph-clj StateGraph |
| `src/care/sim.cljc` | demo driver |
| `test/care/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers case intake through community-care-coordination
regulatory assessment, safeguarding-signal screening, check-in-visit
dispatch and case closure -- the core governed lifecycle this
blueprint's own `docs/business-model.md` names as its Offer:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Case intake + per-jurisdiction community-care-coordination checklisting, HARD-gated on an official spec-basis citation (`:case/intake`/`:careplan/verify`) | Real care-coordination-system integration, real in-home caregiving itself (see `care.facts`'s docstring) |
| Safeguarding-signal screening, evaluated unconditionally so the screening op itself can HARD-hold on its own finding (`:safeguarding/screen`) | Any clinical/social-work judgment itself -- deliberately outside this actor's competence |
| Check-in-dispatch, HARD-gated on full evidence and the assigned caregiver's own workload ceiling, plus a double-dispatch guard (`:actuation/dispatch-checkin`) | |
| Case closure, HARD-gated on full evidence and safeguarding-signal resolution, plus a double-closure guard (`:actuation/close-case`) | |
| Immutable audit ledger for every intake/verification/screening/dispatch/closure decision | |

Extending coverage is additive: add the next gate (e.g. a caregiver-
background-recheck cadence) as its own governed op with its own HARD
checks and tests, following the SAME "an independent governor
re-verifies against the actor's own records before any real-world act"
pattern this repo's flagship op already establishes.

## Jurisdiction coverage (honest)

`care.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `care.facts/catalog` --
currently 4 seeded (JPN, USA, GBR, DEU) out of ~194 jurisdictions
worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. Adding a jurisdiction is
additive: one map entry in `care.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to
make coverage look bigger.

## Maturity

`:implemented` -- `CareOps-LLM` + `Safeguarding Governor` run as real,
tested code (see `Run` above), promoted from the originally-published
`:blueprint`-tier scaffold, modeled closely on the forty-nine prior
actors' architecture. See `docs/adr/0001-architecture.md` for the
history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
