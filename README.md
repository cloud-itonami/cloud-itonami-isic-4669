# cloud-itonami-isic-4669

Open Business Blueprint for **ISIC Rev.5 4669**: Wholesale of Waste and
Scrap and Other Products N.E.C. -- waste-order intake, per-jurisdiction
counterparty-diligence / sanctions regulatory verification, prior-
informed-consent (PIC) verification for transboundary hazardous-waste
movement, waste/scrap dispatch, and invoice settlement for a wholesaler
of waste streams and scrap materials (metal scrap, e-waste, plastics,
paper, and more).

This repository publishes a waste/scrap-wholesale actor -- waste-order
intake, per-jurisdiction contract / sanctions regulatory verification,
prior-informed-consent verification, waste/scrap dispatch and invoice
settlement -- as an OSS business that any qualified operator can fork,
deploy, run, improve and sell, so a regional waste/scrap trader never
surrenders counterparty, credit, sanctions and transboundary-consent
data to a closed waste-trading / ERP SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet, here it is **WasteTradeAdvisor ⊣
Waste Trading Governor**. This blueprint's own
`:itonami.blueprint/governor` keyword, `:waste-trading-governor`, is a
UNIQUE keyword fleet-wide (grep-verified: no other blueprint declares
it) -- a fresh, independent build.

**Like the fuel-wholesale (`cloud-itonami-isic-4671`), general-trading
(`cloud-itonami-isic-4690`), commission-brokerage
(`cloud-itonami-isic-4610`), agri-wholesale (`cloud-itonami-isic-4620`),
provision-trading (`cloud-itonami-isic-4630`), metal-wholesale
(`cloud-itonami-isic-4662`) and textile-wholesale
(`cloud-itonami-isic-4641`) siblings, this vertical is
SELF-CONTAINED**: there is no `kotoba-lang/wastetrade` to delegate
waste-trading validation to, so the credit-clearance / contract-on-file
/ prior-informed-consent / sanctions-screening checks live as direct
entity boolean reads in `wastetrade.governor` (off dedicated
`:credit-cleared?` / `:contract-terms` /
`:transboundary-notification-filed?` /
`:destination-country-consent-documented?` / `:sanctions-screened?`
facts on the `waste-order` record), rather than wrapping an external
capability library's own validated function.

## What makes this vertical genuinely different from every prior sibling

Every prior wholesale-trading sibling in this fleet's core regulated
question is some form of "is this trade/commodity/counterparty
compliant?" -- a jurisdiction's excise/customs law, a commodity's
conflict-minerals provenance, a shipment's forced-labor rebuttable
presumption. This vertical's core regulated question is different in
KIND: **is this shipment of waste allowed to physically cross a border
at all, and does it carry the paper trail the DESTINATION country's own
environmental regulator requires BEFORE the shipment is allowed to
move?** This is an ENVIRONMENTAL transboundary-movement regime (the
Basel Convention's Prior Informed Consent procedure), not a trade-
sanctions, biosecurity, food-safety or human-rights regime -- and,
uniquely in this fleet, it is genuinely BILATERAL: the DESTINATION
country's own competent authority must affirmatively grant consent, not
a fact the exporter can unilaterally obtain or document. See
`docs/adr/0001-architecture.md` Decision 4 for the full reasoning.

> **Why an actor layer at all?** An LLM is great at drafting an order
> summary, normalizing records, and reading a credit file -- but it has
> **no notion of which jurisdiction's customs / export-control law is
> official, no license to dispatch a real waste/scrap shipment across a
> border or settle a real invoice, and no way to know on its own whether
> the counterparty's credit has actually been cleared, whether contract
> terms are actually on file, whether a hazardous-waste shipment
> actually has a filed transboundary-movement notification and a
> documented destination-country competent-authority consent, or whether
> OFAC / equivalent sanctions screening has actually been passed**.
> Letting it dispatch waste or settle an invoice directly invites
> fabricated regulatory citations, a hazardous-waste shipment crossing a
> border with no genuine prior informed consent on file -- exposing the
> operator, the destination country's environment and public health, and
> every downstream party to real enforcement and environmental liability.
> This project seals the WasteTradeAdvisor into a single node and wraps
> it with an independent **Waste Trading Governor**, a human **approval
> workflow**, and an immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers waste-order intake through customs / sanctions
regulatory verification, prior-informed-consent verification,
waste/scrap dispatch and invoice settlement. It does **not**, by itself,
hold any waste-carrier licence, export/import authority or operating
authority required to run a waste/scrap-wholesale business in a given
jurisdiction, and it does not claim to. It also does not perform the
actual physical yard sorting/loadout or route optimization itself, judge
trading-book economics, or perform (or claim to enable) the downstream
recycling/treatment/disposal processing itself -- that is a separate
ISIC activity (e.g. 3830 materials recovery, 24xx metal-recycling
manufacturing), out of scope for this wholesale-trading R0. Freight
routing and trading-book optimization (the blueprint's own
`:optimization` technology) is a follow-up slice, not in this R0.
Whoever deploys and operates a live instance (a qualified trading
supervisor / yard operator) supplies any jurisdiction-specific operating
authority, the real sorting/baling/loader dispatch integration and the
real ERP / accounts-receivable integrations, and bears that
jurisdiction's liability -- the software supplies the governed,
spec-cited, audited execution scaffold so that operator does not have to
build the compliance layer from scratch.

### Actuation

**Dispatching a real waste/scrap shipment across a border to a
counterparty and settling a real waste-trade invoice are never
autonomous, at any phase, by construction.** Two independent layers
enforce this (`wastetrade.governor`'s `:delivery/dispatch`/
`:invoice/settle` high-stakes gate and `wastetrade.phase`'s phase table,
which never puts either op in any phase's `:auto` set) -- see
`wastetrade.phase`'s docstring and `test/wastetrade/phase_test.clj`'s
`delivery-dispatch-never-auto-at-any-phase`/
`invoice-settle-never-auto-at-any-phase`. The actor may draft, check and
recommend; a human trading supervisor is always the one who actually
dispatches a waste/scrap shipment or settles an invoice. Grounded in
transboundary-waste-movement doctrine (the same discipline every
regulator in `wastetrade.facts` codifies: a real dispatch and a real
invoice settlement are human sign-off acts) -- a genuine DUAL-actuation
shape, applied SEQUENTIALLY to the SAME waste-order (dispatch first,
invoice settlement later), unlike `retailops`/4711's own
`:kind`-distinguished alternative-action shape.

## The core contract

```
waste-order intake + jurisdiction facts (wastetrade.facts, spec-cited)
        |
        v
   ┌───────────────────────┐   proposal      ┌────────────────────────────┐
   │ WasteTradeAdvisor      │ ─────────────▶ │ Waste Trading Governor      │  (independent system)
   │ (sealed)               │  + citations    │ spec-basis · evidence-      │
   └───────────────────────┘                 │ incomplete · credit-         │
          │                 commit ◀┼ uncleared · contract-missing ·│
          │                         │ prior-informed-consent-        │
    record + ledger        escalate ┼ missing · counterparty-        │
          │              (ALWAYS for│ sanctions-flag-unresolved ·    │
          │       :delivery/        │ already-dispatched ·           │
          │       dispatch/         │ already-invoiced               │
          │       :invoice/         └────────────────────────────┘
          │       settle)
          ▼
      human approval
```

**The WasteTradeAdvisor never dispatches waste/scrap across a border to
a counterparty or settles an invoice the Waste Trading Governor would
reject, and never does so without a human sign-off.** Hard violations
(fabricated regulatory requirements; unsupported evidence; an uncleared
counterparty credit; no contract-terms on file; a hazardous-waste
shipment with no filed transboundary notification AND documented
destination-country consent; an unresolved sanctions-screening flag; a
double dispatch/invoice) force **hold** and *cannot* be approved past; a
clean dispatch/invoice proposal still always routes to a human.

## Run

```bash
clojure -M:dev:run     # walk one clean consent-verify + dispatch + invoice lifecycle, plus eight HARD-hold/control cases, through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Prior informed consent: the domain-defining check

Unlike every other check in this actor (and every check in the fuel-
wholesale/general-trading/commission-brokerage/agri-wholesale/provision-
trading/metal-wholesale/textile-wholesale siblings), `prior-informed-
consent-missing` verifies a fact that is NOT something the exporter
holds or unilaterally obtains -- it is a BILATERAL government-to-
government act the DESTINATION country's own competent authority must
affirmatively grant. When `:waste-stream-type` is classified HAZARDOUS
(`wastetrade.facts/hazardous-waste-stream?`), `:delivery/dispatch`
HARD-holds unless BOTH `:transboundary-notification-filed?` (the
required movement notification to the destination country's competent
authority) AND `:destination-country-consent-documented?` (that
authority's own documented written consent) are true. This check applies
**unconditionally across every jurisdiction/destination-country
pairing** -- including a destination that is not itself a Party to the
Basel Convention (the USA, which has its OWN parallel RCRA
destination-consent requirement) -- see `wastetrade.governor`'s
namespace docstring and `docs/adr/0001-architecture.md` Decision 4 for
the full reasoning and the three-way contrast with the metal-wholesale
sibling's (jurisdiction-unconditional) and textile-wholesale sibling's
(jurisdiction-gated) own domain-defining checks. It is a NO-OP for every
green-list / non-hazardous waste-stream type (sorted ferrous/non-ferrous
scrap metal, sorted single-resin plastics, sorted paper/cardboard,
sorted clean textile waste, ...);
`test/wastetrade/governor_contract_test.clj`'s
`prior-informed-consent-check-is-a-no-op-for-green-list-waste` proves
this directly with a sorted-ferrous-scrap order carrying the SAME
undocumented PIC facts as a HELD hazardous-waste order.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here an autonomous sorting/baling
robotic system (the same class of AI-driven robotic sorting arm and
magnetic/eddy-current separator already commercially deployed at modern
materials-recovery yards) stages the outbound waste stream, and an
autonomous loader physically performs the outbound loadout, at the
wholesaler's own yard/warehouse dispatch point -- the point at which
this actor's `:delivery/dispatch` occurs -- under the actor, gated by
the independent Waste Trading Governor. This is a materially narrower
physical claim than the DOWNSTREAM shredding/recycling/disposal
processing itself (a separate ISIC activity, out of scope -- see Scope
above); the robot named here operates strictly at THIS actor's own
wholesale-dispatch act. The governor never dispatches hardware itself: a
dispatch-clearing action must have cleared the same sign-off a human
trading supervisor would need. This restates the fleet-wide robotics
premise three ways (ADR-2607011000): the blueprint declares `:robotics
true`, the README names the robot that performs the physical act, and
the Waste Trading Governor is the independent gate that robot's command
must pass -- a robot may sort or stage a waste stream, but only after the
governor and a human supervisor both agree it is safe to dispatch.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Waste Trading Governor, dispatch/invoice draft records, audit ledger |
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
`4669`). Like its wholesale-trading siblings, this vertical is NOT
backed by a separate bespoke domain capability lib: the waste-trading
checks (credit-clearance, contract-on-file, prior-informed-consent,
sanctions-screening) are direct entity boolean reads in
`wastetrade.governor`, on top of the generic robotics/identity/forms/
dmn/bpmn/audit-ledger stack.

## Layout

| File | Role |
|---|---|
| `src/wastetrade/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + dispatch AND invoice history (dual history). The double-actuation guard checks dedicated `:dispatched?`/`:invoiced?` booleans rather than a `:status` value |
| `src/wastetrade/registry.cljc` | Dispatch/invoice draft records (record construction only -- the Waste Trading Governor's checks are direct entity booleans, so there are no pure range-check functions to host here) |
| `src/wastetrade/facts.cljc` | Per-EXPORTING-jurisdiction customs/waste-export catalog with an official spec-basis citation per entry, PLUS a separate `consent-basis` catalog keyed by DESTINATION country (Basel Convention Article 6 PIC for Parties, RCRA import-consent for the seeded USA non-Party), the hazardous/green-list waste-stream classification, and honest coverage reporting |
| `src/wastetrade/wastetradeadvisor.cljc` | **WasteTradeAdvisor** -- `mock-advisor` ‖ `llm-advisor`; intake/consent-verification/dispatch/invoice proposals |
| `src/wastetrade/governor.cljc` | **Waste Trading Governor** -- 6 HARD checks (spec-basis · evidence-incomplete · credit-uncleared · contract-missing · prior-informed-consent-missing · counterparty-sanctions-flag-unresolved) + 2 double-actuation guards + 1 soft (confidence/actuation gate) |
| `src/wastetrade/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted verify → supervised (dispatch/invoice always human; order intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/wastetrade/operation.cljc` | **OperationActor** -- langgraph StateGraph |
| `src/wastetrade/sim.cljc` | demo driver |
| `test/wastetrade/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers waste-order intake through customs / sanctions
regulatory verification, prior-informed-consent verification,
waste/scrap dispatch and invoice settlement -- the core governed
lifecycle:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Waste-order intake + per-jurisdiction evidence checklisting, HARD-gated on an official spec-basis citation (`:order/intake`/`:consent/verify`) | Real weighbridge/customs/ERP integration, freight routing and trading-book economics |
| Waste/scrap dispatch, HARD-gated on full evidence, a credit-cleared counterparty, contract-terms on file, verified prior informed consent (where the waste-stream hazard classification applies), a passed sanctions screen and no double-dispatch (`:delivery/dispatch`) | Downstream recycling/treatment/disposal processing (a separate ISIC activity) |
| Invoice settlement, HARD-gated on full evidence, a passed sanctions screen and no double-invoice (`:invoice/settle`) | |
| Immutable audit ledger for every intake/verification/dispatch/invoice decision | |

Extending coverage is additive: add the next gate (e.g. a waste-tracking-
form / manifest-reconciliation check) as its own governed op with its
own HARD checks and tests, following the SAME "an independent governor
re-verifies against the actor's own records before any real-world act"
pattern this repo's flagship ops already establish.

## Jurisdiction coverage (honest)

`wastetrade.facts/coverage` reports how many requested jurisdictions
actually have an official GENERAL spec-basis in `wastetrade.facts/
catalog` -- currently 4 seeded (JPN, USA, GBR, DEU) out of ~194
jurisdictions worldwide. The SEPARATE `wastetrade.facts/consent-basis`
catalog seeds a genuinely BINDING prior-informed-consent regime for ALL
4 of those (unlike the metal-wholesale sibling's non-statutory OECD
fallback) -- 3 as Basel Convention Parties (JPN, GBR, DEU) citing Basel
Article 6, and 1 (USA, a Basel non-Party) citing its own parallel RCRA
import-consent regime. See `docs/business-model.md`'s "Jurisdiction
coverage (honest)" section for the full confidence breakdown -- I do not
have live web access; citations are drawn from training-time knowledge
with varying confidence, flagged explicitly where I am not fully
certain. Adding a jurisdiction is additive: one map entry in
`wastetrade.facts/catalog` (and, where a genuine consent regime exists,
one entry in `consent-basis`), citing a real official source -- never
fabricate a jurisdiction's requirements to make coverage look bigger.

## Maturity

`:implemented` -- `WasteTradeAdvisor` + `Waste Trading Governor` run as
real, tested code (see `Run` above), following the SAME governed-actor
architecture as the other prior actors across this fleet, with its own
distinct, independently-named governor and its own direct-entity-boolean
waste-trading checks -- including the fleet's first BILATERAL,
government-to-government-consent-gated domain-defining check. See
`docs/adr/0001-architecture.md` for the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
