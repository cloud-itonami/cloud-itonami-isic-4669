# ADR-0001: WasteTradeAdvisor ⊣ Waste Trading Governor architecture

## Status

Accepted. `cloud-itonami-isic-4669` published directly as `:implemented`
in the `kotoba-lang/industry` registry.

## Context

`cloud-itonami-isic-4669` publishes an OSS business blueprint for
wholesale of waste and scrap and other products n.e.c. (waste-order
intake, per-jurisdiction contract / sanctions regulatory verification,
prior-informed-consent verification for transboundary hazardous-waste
movement, dispatch, and invoice settlement). Like every prior actor in
this fleet, the blueprint alone is not an implementation: this ADR
records the governed-actor architecture that establishes it as real,
tested code, following the same langgraph StateGraph + independent
Governor + Phase 0->3 rollout pattern established by
`cloud-itonami-isic-6511` (life insurance) and applied across many prior
siblings, most directly the PRINCIPAL wholesale-trading siblings:
`cloud-itonami-isic-4671` (fuel wholesale, single-commodity excise/
sanctions focus), `cloud-itonami-isic-4690` (general/diversified
wholesale trading, multi-commodity export-control/sanctions focus),
`cloud-itonami-isic-4620` (agri-wholesale, biosecurity, kind-gated
certificate split), `cloud-itonami-isic-4630` (provision trading,
category-gated split), `cloud-itonami-isic-4662` (metal wholesale,
conflict-minerals chain-of-custody, COMMODITY-TYPE-gated,
UNCONDITIONAL-across-jurisdiction check), and `cloud-itonami-isic-4641`
(textile wholesale, forced-labor rebuttable presumption,
JURISDICTION-GATED check).

ISIC 4669 is a PRINCIPAL trading model like 4671/4690/4620/4630/4662/
4641 -- the wholesaler takes title and resells. Its defining regulatory
exposure is genuinely different IN KIND from every one of those
siblings, not merely a new value on an existing gating axis: the core
regulated event is not "is this trade/commodity/counterparty compliant"
at all. It is **whether a shipment of waste is allowed to physically
cross a border in the first place, and whether it carries the paper
trail the DESTINATION country's own environmental regulator requires
BEFORE the shipment is allowed to move** -- the Basel Convention's Prior
Informed Consent (PIC) procedure, an ENVIRONMENTAL transboundary-
movement regime, not a trade-sanctions, biosecurity, food-safety or
human-rights regime. It is also, uniquely in this fleet, genuinely
BILATERAL: every prior sibling's domain-defining check re-verifies a
fact the EXPORTER holds or unilaterally obtains (chain-of-custody
documentation, a rebuttal evidence dossier, a phytosanitary certificate,
a food-safety certificate) -- Prior Informed Consent is a government-to-
government act the DESTINATION country's own competent authority must
affirmatively grant. This is why this vertical's domain-defining check
is gated on the waste-stream's OWN hazard classification (a commodity
property, like the metal-wholesale sibling) but reads a BILATERAL pair
of facts (unlike any prior sibling) -- see Decision 4.

Like the metal-wholesale and textile-wholesale siblings, this vertical
has NO bespoke domain capability library in `kotoba-lang` to wrap
(verified: no `kotoba-lang/wastetrade`-style repo exists, and
`kotoba-lang/robotics` is the generic cross-cutting robotics contract
every cloud-itonami vertical already uses, not a domain-specific library
for this vertical). This build therefore uses self-contained domain
logic. The waste-trading checks (credit-clearance, contract-on-file,
prior-informed-consent, sanctions-screening) are direct entity boolean
reads in `wastetrade.governor`, off dedicated `:credit-cleared?` /
`:contract-terms` / `:transboundary-notification-filed?` /
`:destination-country-consent-documented?` / `:sanctions-screened?`
facts on the `waste-order` record -- NO pure range-check functions are
needed (contrast the crude-extraction sibling, whose registry hosts its
reservoir/annular/water-cut/H2S range checks).

This blueprint's own `:itonami.blueprint/governor` keyword,
`:waste-trading-governor`, is grep-verified UNIQUE among the actor fleet
repos checked out at build time -- no naming-collision precedent
question, a fresh independent build.

## Decision

### Decision 1: fresh governor identity, no reuse precedent needed

`:waste-trading-governor` is grep-verified unique across every
`blueprint.edn` checked out locally at build time. This build follows
the SAME governed-actor architecture as every prior actor, but with its
own distinct governor identity.

### Decision 2: self-contained domain logic, direct entity booleans (no `kotoba-lang/wastetrade` to wrap, and no range-check functions to host)

Like the fuel-wholesale, general-trading, commission-brokerage, agri-
wholesale, provision-trading, metal-wholesale and textile-wholesale
siblings (and unlike the crude-extraction sibling, which hosts pure
physical range-check functions in its registry because its governor
re-verifies measured physical values), this waste/scrap-wholesale
vertical needs no range-check functions: there is no pre-existing
waste-trading capability library to delegate to, AND the governor's
domain checks (credit-clearance, contract-on-file, prior-informed-
consent, sanctions-screening) are direct entity boolean reads off the
`waste-order` record's own dedicated facts -- not measured-value-vs-
limit range comparisons. So `wastetrade.registry` is RECORD
CONSTRUCTION ONLY (no range-check functions), and `wastetrade.governor`
reads the order's booleans directly.

### Decision 3: dual-actuation shape, SEQUENTIAL on the SAME `waste-order` entity

Like the fuel-wholesale sibling's `fuel-order` entity, the metal-
wholesale sibling's `metal-order` entity and the textile-wholesale
sibling's `textile-order` entity, this vertical's `dispatch` and
`settle` actuation events apply SEQUENTIALLY to the SAME `waste-order`
-- a waste/scrap shipment dispatch happens first (the shipment leaves
the wholesaler's own yard/warehouse and crosses the border), invoice
settlement happens later (the money side of the trade, custody /
financial transfer), on the same order record. `high-stakes` is
`#{:delivery/dispatch :invoice/settle}`; neither ever auto-commits at
any phase.

### Decision 4: `prior-informed-consent-missing` -- gated on WASTE-STREAM HAZARD TYPE, unconditional across jurisdiction; the defining design decision of this build, and a genuinely new fact SHAPE for this fleet

This is the decision that most distinguishes this vertical from every
prior wholesale-trading sibling. It required not just a new gating
value, but a genuinely new FACT SHAPE: unlike every prior sibling's
domain-defining check (a UNILATERAL fact the exporter holds or obtains:
chain-of-custody documentation, a rebuttal evidence dossier, a
phytosanitary certificate), Prior Informed Consent is a BILATERAL
government-to-government act the destination country's own competent
authority must affirmatively grant. This build had to answer two
separate questions the metal-wholesale and textile-wholesale ADRs did
not have to distinguish: (a) what GATES whether the check fires at all
(a property of the waste stream, or of the jurisdiction pairing?), and
(b) is the check UNCONDITIONAL or JURISDICTION-GATED once it does fire?
The metal-wholesale sibling's conflict-minerals check answered both with
"commodity property, unconditional." The textile-wholesale sibling's
forced-labor check answered both with "shipment property (origin/
entity), but the CHECK itself is jurisdiction-gated because the
underlying legal mechanism (UFLPA) is a single-country border-detention
statute." This build had to independently re-derive the answer for
Prior Informed Consent, not copy either precedent uncritically.

**Why the GATING property is the waste-stream's hazard classification
(a commodity property), matching the metal-wholesale sibling's shape.**
The Basel Convention's Prior Informed Consent procedure attaches to
WHETHER THE WASTE ITSELF is classified hazardous (Basel Annex I/III/
VIII, "List A"), not to which two specific countries are trading it --
a WEEE/e-waste shipment from Japan to Germany and the identical WEEE/
e-waste shipment from Japan to the USA face the SAME real-world
question (does this shipment have a filed notification and documented
destination consent?), while a sorted-ferrous-scrap shipment on either
route faces neither requirement, because sorted ferrous scrap moves
under the Convention's own lighter Annex IX / OECD "green list" regime.
This is structurally the SAME shape the metal-wholesale sibling's
Decision 4 chose (Option C: metal-type-gated) and explicitly the shape
its Option B ("jurisdiction-gated conflict-minerals check") rejected --
see that ADR's Decision 4 for the parallel reasoning.

**Why the CHECK is UNCONDITIONAL across jurisdiction/destination-country
pairing -- the key point of contrast with the textile-wholesale
sibling.** The textile-wholesale sibling's forced-labor check is
deliberately JURISDICTION-GATED (fires only when the order's OWN
`:jurisdiction` is the USA, because UFLPA is specifically a U.S. Customs
and Border Protection BORDER-DETENTION mechanism under 19 U.S.C. §1307
-- it does not attach to a dispatch whose own customs-entry act happens
in a different country). Prior Informed Consent is different in kind
from UFLPA in exactly the dimension that made UFLPA's gating correct:
the Basel Convention is not a single country's border-enforcement
statute -- it is a multilateral treaty with ~190 Parties, the broadest
ratification of any environmental convention establishing this kind of
control system, and its PIC mechanism attaches to the DESTINATION
country's own competent authority REGARDLESS of which two specific
Parties (or Party/non-Party pair) are involved. Three design options
were considered:

- **Option A (rejected): fold prior-informed-consent evidence into the
  generic `evidence-incomplete-violations` checklist**, adding two
  conditional items (`transboundary notification`, `destination consent`)
  to `wastetrade.facts/catalog`'s per-jurisdiction `:required-evidence`.
  Rejected for the SAME reason the metal-wholesale sibling's ADR
  rejected the analogous option: this would force prior-informed-consent
  diligence to inherit EXPORTING-jurisdiction-only gating it does not
  actually have (the real fact is a relationship between the EXPORTING
  jurisdiction and the specific `:destination-country`, not a property
  of the exporting jurisdiction alone), and would force every
  jurisdiction's generic checklist to carry conditional items irrelevant
  to most (non-hazardous) orders.
- **Option B (rejected): a jurisdiction-gated check, copying the
  textile-wholesale sibling's shape exactly** -- firing only when the
  order's own `:jurisdiction` (or `:destination-country`) has a BINDING
  statute seeded in `consent-basis` AND is, specifically, a Basel Party.
  Rejected: this would produce the SAME dishonest and operationally
  dangerous result the metal-wholesale sibling's ADR rejected for its
  own analogous Option B -- a WEEE/e-waste order destined for a Basel
  Party would HOLD without documented consent, but the IDENTICAL order
  destined for the USA (seeded as a Basel non-Party) would dispatch with
  NO consent-check at all, implying the USA has no destination-consent
  requirement whatsoever. This is factually wrong: the USA has its OWN
  PARALLEL, genuinely binding destination-consent requirement under RCRA
  (40 CFR §262.83/§262.84) -- gating the check off Basel-Party status
  specifically would make this actor blind to a real, binding U.S. legal
  requirement merely because the CITATION differs from the Basel
  Convention's. Unlike UFLPA (a mechanism that genuinely does NOT attach
  outside the USA's own border), destination-consent-before-hazardous-
  import is a requirement this build's own research found to exist,
  in some binding form, at EVERY seeded destination -- so gating the
  CHECK on it would be gating on something that is, in this catalog,
  always true, making the gate pointless at best and a silent
  bug-in-waiting at worst (the moment a NEW destination is seeded with
  `:binding? false` or omitted from `consent-basis` entirely, this
  option would silently let that destination's hazardous shipments skip
  the check rather than correctly holding for lack of evidence).
- **Option C (chosen): a waste-stream-hazard-type-gated check, evaluated
  UNCONDITIONALLY across every jurisdiction/destination-country pairing**
  -- `prior-informed-consent-missing-violations` fires whenever
  `wastetrade.facts/hazardous-waste-stream?` is true for the order's
  `:waste-stream-type`, regardless of `:jurisdiction` or
  `:destination-country`, and requires BOTH
  `:transboundary-notification-filed?` AND
  `:destination-country-consent-documented?` to be true. The CITATION
  shown to a human reviewer still varies honestly by destination country
  (`wastetrade.facts/consent-citation`: Basel Convention Article 6 for a
  Party, RCRA import-consent for the seeded non-Party USA) -- but the
  CHECK itself does not, matching how a real transboundary hazardous-
  waste trader must actually operate: verify SOME form of documented
  destination consent exists before ANY hazardous shipment crosses ANY
  border, because a destination country's right to refuse entry of
  hazardous waste it has not consented to is close to universal, whether
  codified through Basel Article 6 or a national-equivalent regime.
  `wo-6` in the demo data (`wastetrade.store/demo-data`) proves the
  UNCONDITIONAL-across-jurisdiction shape directly: a hazardous
  (used-lead-acid-battery) order destined for the USA -- a Basel
  non-Party -- with neither PIC sub-fact on file HOLDS exactly like one
  destined for a Basel Party would, because this actor correctly checks
  for RCRA-style consent rather than assuming "non-Party means no
  check applies."

The check additionally folds TWO distinct real-world sub-requirements (a
filed transboundary-movement notification; a documented destination-
country competent-authority consent) into ONE named governor rule rather
than two, because both are arms of the SAME real-world Prior Informed
Consent procedure -- Basel Article 6 itself treats notification and
consent as two SEQUENTIAL steps of ONE bilateral act, not two
independent regimes, and a dispatch is equally unsafe whether the
notification or the destination consent is the one missing. This
mirrors the SAME "fold two sub-requirements into one named rule" 
discipline the metal-wholesale sibling's `conflict-minerals-provenance-
unverified-violations` and the textile-wholesale sibling's `forced-
labor-presumption-unrebutted-violations` checks establish -- the
`:detail` string still names which sub-fact specifically failed, so no
audit-ledger precision is lost. `wo-8` in the demo data proves this
directly: a hazardous chemical-waste order with the notification FILED
but destination consent still undocumented STILL HOLDS, exactly like
`wo-6` (neither sub-fact on file) does.

**Why this check is NOT a rebuttable presumption (unlike the textile-
wholesale sibling's).** The textile-wholesale sibling's forced-labor
check implements a REBUTTABLE PRESUMPTION: a flagged origin/entity can
overcome the presumption with a sufficient rebuttal dossier, so the same
flagged shipment either holds or dispatches depending on evidence
quality. Prior Informed Consent has no analogous "rebuttal" concept --
either the destination country's competent authority has actually
consented (in which case the shipment is fully compliant, not merely
"presumption overcome"), or it has not (in which case the shipment
simply may not proceed, full stop). This is a genuinely different
regulatory SHAPE from a rebuttable presumption -- documented here so it
is not mistaken for a re-application of the textile-wholesale sibling's
mechanic under a different name.

This makes ISIC 4669 the first vertical in this fleet's wholesale-
trading cluster whose domain-defining check reads a genuinely BILATERAL
fact (a destination country's own affirmative government act) rather
than a unilateral fact the exporter holds -- a structurally new fact
SHAPE, layered on top of the metal-wholesale sibling's already-
established "commodity-property-gated, jurisdiction-unconditional"
GATING shape (Decision 4's answer to question (a) matches 4662;
Decision 4's answer to question (b), UNCONDITIONAL, also matches 4662,
but for a genuinely re-derived reason specific to Basel's near-universal
ratification and the honest discovery that even the one seeded non-Party
has its own equivalent regime -- not a mechanical copy of 4662's
reasoning).

### Decision 5: `counterparty-sanctions-flag-unresolved?` -- the open-flag-unresolved discipline (reapplied, not new)

An unresolved sanctions-screening flag -- the counterparty has not
passed OFAC / equivalent sanctions screening -- is a HARD,
un-overridable hold. This reuses the SAME open-flag-unresolved
discipline the freight sibling's `delivery-exception-unresolved?` check
(and the fuel-wholesale/general-trading/commission-brokerage/agri-
wholesale/provision-trading/metal-wholesale/textile-wholesale siblings'
own sanctions checks) establish -- an open concern cannot be silently
suppressed to force a dispatch or invoice through. Evaluated
UNCONDITIONALLY at both `:delivery/dispatch` and `:invoice/settle`, and
UNCONDITIONALLY regardless of waste-stream hazard classification (unlike
the prior-informed-consent check in Decision 4, sanctions screening
applies uniformly to all waste-stream types -- there is no regulatory
reason to differentiate it by commodity, only the prior-informed-consent
requirement itself is hazard-type-specific).

### Decision 6: dedicated double-actuation-guard booleans

`:dispatched?` / `:invoiced?` are dedicated booleans on the
`waste-order` record, never a single `:status` value -- the same
discipline every prior governor's guards establish, informed by
`cloud-itonami-isic-6492`'s real status-lifecycle bug
(ADR-2607071320).

### Decision 7: Store protocol, MemStore + DatomicStore parity

`wastetrade.store/Store` is implemented by both `MemStore` (atom-
backed, default for dev/tests/demo) and `DatomicStore` (`langchain.db`-
backed), proven to satisfy the same contract in
`test/wastetrade/store_contract_test.cljk`. The ledger stays append-only
on every backend: which waste-order was verified for a jurisdiction with
no official spec-basis, which counterparty had credit-uncleared / no
contract / unverified prior informed consent / an unresolved sanctions-
screening flag, which order was dispatched, which invoice was settled,
on what jurisdictional and bilateral-consent basis, approved by whom --
always a query over an immutable log.

### Decision 8: Phase 0->3 with `:delivery/dispatch`/`:invoice/settle` NEVER auto

`wastetrade.phase`'s phase table puts `:order/intake` (no direct capital
risk) in phase 3's `:auto` set as its only member; `:delivery/dispatch`
and `:invoice/settle` are deliberately ABSENT from every phase's `:auto`
set, including phase 3 -- a permanent structural fact.
`wastetrade.governor`'s high-stakes gate enforces the same invariant
independently: two layers agree that actuation is always a human
trading supervisor's call.

### Decision 9: mock + LLM advisor pair

`wastetrade.wastetradeadvisor` provides a deterministic `mock-advisor`
(default, runs offline) and an `llm-advisor` backed by a
`langchain.model/ChatModel`. The LLM advisor's EDN proposal is parsed
defensively: any parse/shape failure yields a safe low-confidence noop
so the governor escalates/holds -- an LLM hiccup can never auto-dispatch
waste/scrap or auto-settle an invoice. The mock advisor's `verify-
consent` proposal drafts the prior-informed-consent citation
informationally (via `wastetrade.facts/consent-citation`) for a human
reviewer's benefit, and separately surfaces the private R2v3/e-Stewards
e-waste certification schemes informationally -- but neither citation is
EVER what the governor checks at `:delivery/dispatch` -- the governor
independently re-reads the order's own
`:transboundary-notification-filed?`/
`:destination-country-consent-documented?` ground truth directly
(Decision 4), so a compromised or mistaken advisor citation can never
substitute for the real facts.

### Decision 10: `:robotics true`, reasoned separately for this vertical's own yard-sorting/dispatch point

`:itonami.blueprint/robotics` is `true`, a deliberate call reasoned
specifically for this vertical rather than copied from a sibling
default -- following the SAME kind-differentiated reasoning discipline
the metal-wholesale sibling's Decision 10 establishes. Real modern
waste/scrap-wholesale yards and materials-recovery facilities already
run substantial, commercially-deployed physical automation at the
sorting/dispatch point (AI-driven robotic sorting arms, magnetic and
eddy-current separators, automated baling systems) -- a genuine,
well-documented automation claim, not a speculative one. This claim is
deliberately scoped to THIS actor's own wholesale-dispatch act (the
sorting/staging/loadout that happens at the point `:delivery/dispatch`
occurs), explicitly EXCLUDING the downstream recycling/shredding/
smelting/disposal processing that happens after a shipment reaches its
destination -- that processing is a separate ISIC activity (e.g. ISIC
3830 materials-recovery/recycling manufacturing, or 24xx metal
recycling), out of scope for this wholesale-trading R0 (mirroring the
metal-wholesale sibling's own upstream/midstream/downstream scoping
discipline for mining vs. wholesale vs. smelting).

## Alternatives considered

- **Wrapping a bespoke `kotoba-lang/wastetrade` capability library.**
  Considered and explicitly ruled out: no such library exists, and
  `kotoba-lang/robotics` is generic, not waste-trading-specific. Forcing
  a false capability-library integration would be dishonest; this build
  correctly uses self-contained domain logic instead.
- **Hosting pure range-check functions in the registry (as the crude
  sibling does).** Considered and ruled out: the waste-trading domain
  checks are direct entity booleans (credit cleared? contract on file?
  consent verified? sanctions screened?), not measured-value-vs-limit
  range comparisons, so there are no range checks to host.
  `wastetrade.registry` is record construction only.
- **Folding prior-informed-consent evidence into the generic
  jurisdiction evidence checklist, or gating the check on Basel-Party
  status of the destination.** Considered and rejected -- see Decision 4
  Options A and B above for the full reasoning: both would misrepresent
  destination-consent risk as either a property of the exporting
  jurisdiction alone, or as absent entirely for a non-Party destination
  that in fact has its own equivalent binding regime.
- **Treating R2v3/e-Stewards as if they were legally binding
  requirements.** Considered and rejected as dishonest -- neither is a
  government statute in any jurisdiction seeded here. Both are surfaced
  purely informationally, honestly labeled `:binding? false`, exactly
  the same discipline `consent-basis` uses to distinguish genuinely
  binding law from any non-statutory baseline.
- **A `:kind`-distinguished entity for dispatch vs. invoice** (matching
  the retail sibling's `order` shape). Rejected: dispatch and invoice
  settlement happen SEQUENTIALLY on the SAME waste-order in this domain,
  not as alternative actions -- the fuel-wholesale, metal-wholesale and
  textile-wholesale siblings' sequential shape is the honest match here.
- **Modeling prior informed consent as a rebuttable presumption**
  (copying the textile-wholesale sibling's mechanic). Considered and
  rejected: Basel PIC has no "rebuttal" concept -- consent either exists
  or it does not; there is no evidentiary dossier that overcomes an
  absent government consent the way a forced-labor rebuttal dossier can
  overcome a flagged-origin presumption. Modeling it as rebuttable would
  misrepresent the underlying legal mechanism.
- **Defaulting `:robotics` to `false`** (matching the general-trading and
  commission-brokerage siblings' non-physical intermediation shape).
  Considered and rejected: this vertical's `:delivery/dispatch` is a
  genuine physical act (a waste/scrap shipment actually leaving a
  wholesale yard via automated sorting/baling/loadout), closer in kind
  to the fuel-wholesale/metal-wholesale/textile-wholesale siblings'
  physical dispatch acts -- see Decision 10.
- **Building yard-slotting/freight-routing and trading-book optimization
  in this R0.** Rejected in favor of a scoped R0 slice (the
  `:optimization` capability is correctly marked required, the
  integration is a follow-up), consistent with this fleet's 'extending
  coverage is additive' convention.

## Consequences

- Fresh independent actor in this fleet, following the SAME governed-
  actor architecture as every prior sibling.
- Establishes the waste-trading checks as direct entity boolean reads
  (no pure range-check functions needed), an honest structural
  differentiator from the crude-extraction sibling's registry-hosted
  physical range checks.
- Establishes the fleet's first BILATERAL, government-to-government-
  consent-gated domain-defining check (Decision 4) -- a genuinely new
  fact SHAPE distinct from every prior sibling's unilateral-fact checks,
  while reusing the metal-wholesale sibling's commodity-property-gated,
  jurisdiction-unconditional GATING shape for a re-derived (not copied)
  reason specific to the Basel Convention's near-universal ratification.
  A template for any future vertical whose defining regulatory concern
  is itself a bilateral or multilateral government act rather than a
  fact the regulated party can unilaterally obtain.
- `MemStore` || `DatomicStore` parity is proven by
  `test/wastetrade/store_contract_test.cljk`.
- 46 tests / 228 assertions pass; lint is clean; the demo
  (`kbb -M:dev:run`) walks one clean consent-verify + dispatch +
  invoice lifecycle, six HARD-hold scenarios (no spec-basis, credit-
  uncleared, contract-missing, prior-informed-consent-missing, sanctions,
  double dispatch, double invoice), PLUS a control scenario (sorted
  ferrous scrap with the same undocumented PIC facts as a held hazardous
  order, dispatching cleanly) proving the check is genuinely
  hazard-type-gated, PLUS a partial-satisfaction scenario (notification
  filed but destination consent undocumented, still holding) proving
  both sub-facts are required, end-to-end.
- `blueprint.edn`'s `:robotics true` is a reasoned, vertical-specific
  call scoped to this actor's own yard-sorting/dispatch point,
  documented in README and `docs/business-model.md` as explicitly
  distinct from out-of-scope downstream recycling/processing.

## References

- `cloud-itonami-isic-6511/docs/adr/0001-architecture.md` (origin of the
  general governed-actor architecture pattern)
- `cloud-itonami-isic-4671/docs/adr/0001-architecture.md` (fuel-
  wholesale sibling; origin of the sequential dual-actuation shape and
  the self-contained-domain-logic pattern this build follows most
  closely)
- `cloud-itonami-isic-4690/docs/adr/0001-architecture.md` (general-
  trading sibling)
- `cloud-itonami-isic-4610/docs/adr/0001-architecture.md` (commission-
  brokerage sibling; origin of the 'a genuinely new regulatory concern
  gets its own named check' precedent)
- `cloud-itonami-isic-4620/docs/adr/0001-architecture.md` (agri-
  wholesale sibling; origin of the fleet's first kind-gated certificate
  split)
- `cloud-itonami-isic-4630/docs/adr/0001-architecture.md` (provision-
  trading sibling; origin of the fleet's first many-to-one category-
  gated split and the 'fold two sub-requirements into one named rule'
  precedent this build's Decision 4 follows for the prior-informed-
  consent check)
- `cloud-itonami-isic-4662/docs/adr/0001-architecture.md` (metal-
  wholesale sibling; origin of the fleet's first commodity-property-
  gated, jurisdiction-UNCONDITIONAL domain-defining check -- this
  build's Decision 4 reuses this GATING shape, for a re-derived reason)
- `cloud-itonami-isic-4641/docs/adr/0001-architecture.md` (textile-
  wholesale sibling; origin of the fleet's first JURISDICTION-GATED
  domain-defining check and the rebuttable-presumption mechanic -- this
  build's Decision 4 explicitly considers and rejects both shapes as a
  match for Prior Informed Consent, and documents why)
- `cloud-itonami-isic-0610/docs/adr/0001-architecture.md` (crude-
  extraction sibling; contrast: hosts pure physical range-check
  functions in its registry, which this vertical does NOT need)
- Basel Convention on the Control of Transboundary Movements of
  Hazardous Wastes and Their Disposal (adopted 22 March 1989, entered
  into force 5 May 1992, ~190 Parties), Article 6 Prior Informed Consent
  procedure (UNEP Secretariat)
- 廃棄物の処理及び清掃に関する法律 (Waste Management and Public Cleansing
  Act); 特定有害廃棄物等の輸出入等の規制に関する法律 (Act on the Control of
  Export, Import and Others of Specified Hazardous Wastes and Other
  Wastes, Japan's Basel-implementing statute) (Japan, 環境省 / MOF
  Customs / METI)
- Resource Conservation and Recovery Act (RCRA, 42 U.S.C. §6901 et
  seq.); hazardous waste export/import requirements, 40 CFR Part 262
  Subpart H (US, EPA); OECD Council Decision C(2001)107/FINAL on the
  Control of Transboundary Movements of Wastes Destined for Recovery
  Operations (OECD)
- Transfrontier Shipment of Waste Regulations 2007 (SI 2007/1711, as
  amended) (UK, Environment Agency / DEFRA)
- Regulation (EU) 2024/1157 on shipments of waste, recast of Regulation
  (EC) No 1013/2006 (EU; Germany, Umweltbundesamt / Zoll)
- R2v3 (Responsible Recycling) certification standard (Sustainable
  Electronics Recycling International, SERI) -- PRIVATE, VOLUNTARY, not
  binding law
- e-Stewards certification standard (Basel Action Network, BAN) --
  PRIVATE, VOLUNTARY, not binding law
