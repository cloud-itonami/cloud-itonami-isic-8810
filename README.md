# cloud-itonami-8810

Open Business Blueprint for **ISIC Rev.5 8810**: social work activities
without accommodation for older persons and persons with disabilities.

This repository designs a forkable OSS business for community care
coordination, check-ins, benefits navigation, caregiver scheduling and
non-residential support operations.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a care-coordination robot performs check-in, reminders and assistive support for care recipients under an actor that proposes
actions and an independent **Safeguarding Governor** that gates them. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions (such as
operating near elders, care recipients or in homes) require human sign-off.

A live sample of the operator console (robotics safety console, shared template) is rendered in [docs/samples/operator-console.html](docs/samples/operator-console.html) — pure-data HTML output of `kotoba.robotics.ui`.

## Core Contract

```text
person-centered plan + consent + service directory + visit/check-in records
        |
        v
Care Support Advisor -> Safeguarding Governor -> task, hold, or escalate
        |
        v
support ledger + caregiver queue
```

The advisor can coordinate support tasks, but cannot hide safeguarding signals,
override consent, or make eligibility/discharge decisions without review.

## Runbook

- Start with consented care plans and synthetic cases.
- Add check-in and task queues.
- Add caregiver and service matching.
- Add safeguarding escalation and audit reports.

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

Code and implementation templates are AGPL-3.0-or-later.
