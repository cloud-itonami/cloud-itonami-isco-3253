# cloud-itonami-isco-3253

Open Occupation Blueprint for **ISCO-08 3253**: Community Health Workers.

This repository designs a forkable OSS business for an independent community health worker: screen residents, make referrals, and follow up on access gaps in underserved areas, with a screening robot performing on-site physical intake work.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here an outreach and screening robot performs check-in, basic vitals capture and wayfinding at community sites under an actor that proposes
actions and an independent **Community Health Governor** that gates them. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions (such as
operating near vulnerable residents or handling sensitive health disclosures) require human sign-off.

A live sample of the operator console (robotics safety console, shared template) is rendered in [docs/samples/operator-console.html](docs/samples/operator-console.html) — pure-data HTML output of `kotoba.robotics.ui`.

## Core Contract

```text
resident consent + screening protocol + provider directory
        |
        v
Outreach Advisor -> Community Health Governor -> refer, hold, or human follow-up
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, suppress
an operating record, or disclose sensitive data without governor approval and
audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `3253`). Required capabilities:

- :robotics
- :identity
- :forms
- :dmn
- :bpmn
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## Reference implementation

`src/community_health/{store,governor}.cljc` is a minimal but real
implementation of the Core Contract above (pure cljc, no external deps):

- `community-health.store` — `Store` protocol + `MemStore`: residents,
  screenings, referrals. A screening/referral can only be recorded against
  a registered (consented) resident (consent provenance).
- `community-health.governor` — `CommunityHealthGovernor`: `assess` gates a
  proposal against the resident-consent env. Hard invariants force `:hold`
  (no resident, direct-write instead of `:propose`); referrals flagged
  `:urgent? true` **always** escalate to `:human-approval` regardless of
  safety-class or confidence — this cannot be suppressed;
  `:high`/`:safety-critical` and low-confidence proposals also escalate.

```bash
clojure -M:test   # 7 tests, 13 assertions, green
```

This repo's own `blueprint.edn` currently declares `:itonami.blueprint/maturity
:blueprint`, not `:implemented`: `store`/`governor` are real, but there is no
compiled `langgraph-clj` StateGraph, Advisor protocol, or audit ledger wired
around them yet, so the actor cannot yet run an end-to-end proposal ->
governor -> commit/hold cycle. Any `:implemented`-tier listing for this repo
in [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
predates that correction and should be treated as stale until the missing
StateGraph/Advisor/ledger layer is built.

## License

AGPL-3.0-or-later.
