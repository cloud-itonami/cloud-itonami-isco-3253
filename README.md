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

`src/community_health/{store,governor,operation,ledger}.kotoba` is a minimal but real
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
  The urgent flag is read from the record being written as well as from
  the proposal, and a record naming a resident other than the one whose
  consent was checked is held (`:record-resident-mismatch`).
- `community-health.operation` — the only path from a proposal to a
  record: `submit!` writes on `:proceed` only; `:human-approval` writes
  nothing until `approve!` by a named approver (once per entry; a `:hold`
  cannot be signed over).
- `community-health.ledger` — append-only audit ledger: every decision,
  approval and refused approval, each with its gapless `:seq`.

```bash
kbb -M:test   # run_tests.kotoba: loads the .kotoba sources, runs every *-test ns
```

20 tests / 55 assertions green. `run_tests.kotoba` refuses (exit 2) to
report a pass on a run below that count, and exits 1 on any failure.
(`test/community_health/physics_spec_test.cljk` is the physical-AI bot's
spec and is not part of this suite.)

This repo's own `blueprint.edn` currently declares `:itonami.blueprint/maturity
:blueprint`, not `:implemented`: `store`/`governor`/`operation`/`ledger` are
real and the proposal -> governor -> commit/hold -> ledger cycle runs, but
there is no compiled `langgraph-clj` StateGraph or Advisor protocol
producing the proposals yet. Any `:implemented`-tier listing for this repo
in [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
predates that correction and should be treated as stale until the missing
StateGraph/Advisor/ledger layer is built.

## License

AGPL-3.0-or-later.
