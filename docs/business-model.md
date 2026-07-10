# Business Model: Wholesale of Waste and Scrap and Other Products N.E.C.

## Classification
- Repository: `cloud-itonami-isic-4669`
- ISIC Rev.5: `4669` — wholesale of waste and scrap and other products n.e.c.
- Domain: `midstream/waste-scrap-wholesale`
- Social impact: environmental protection, public health, transparency
- Governor: `:waste-trading-governor`
- License: AGPL-3.0-or-later

## Scope
This actor covers waste-order intake through per-jurisdiction contract /
sanctions regulatory verification, prior-informed-consent (PIC)
verification for transboundary movement of hazardous waste streams,
waste/scrap dispatch (a shipment leaving the wholesaler's own yard/
warehouse and crossing a border to a counterparty), and invoice
settlement (the money side of the trade, custody / financial transfer)
for a wholesaler of waste streams and scrap materials -- metal scrap
(ferrous and non-ferrous), electronic waste (WEEE), plastics, paper,
used batteries, and other products not elsewhere classified. This
vertical sits MIDSTREAM: waste generation/collection is upstream (a
separate ISIC code), downstream recycling/treatment/disposal processing
is a manufacturing/services step (e.g. ISIC 3830, 24xx), and this ISIC
4669 wholesale trade moves the material in between. It does **not**, by
itself, hold any waste-carrier licence, export/import authority or
operating authority required to run a waste/scrap-wholesale business in
a given jurisdiction, perform the actual physical yard sorting/loadout,
or judge trading-book economics (freight routing and trading-book
optimization is a follow-up slice, not this R0). Whoever deploys a live
instance supplies the jurisdiction-specific operating authority, the
real sorting/baling/loader dispatch equipment and ERP / accounts-
receivable integrations, and bears that jurisdiction's liability -- the
software supplies the governed, spec-cited, audited execution scaffold
so the operator does not have to build the compliance layer from
scratch.

## Customer
- regional and independent waste/scrap wholesalers and materials-
  recovery-yard operators
- e-waste (WEEE) processors and metal-scrap exporters leaving closed
  waste-trading / ERP SaaS
- recyclers and traders who need Basel-Convention-aware dispatch controls
  their generic warehouse-management system does not enforce
- counterparties, banks, destination-country competent authorities and
  environmental regulators who need an auditable, spec-cited,
  consent-cited trade record

## Offer
- waste-order intake and directory management, across hazardous streams
  (e-waste, used batteries, hazardous chemical waste) and green-list
  streams (sorted scrap metal, sorted plastics, sorted paper) in one
  system
- per-jurisdiction contract / sanctions regulatory verification with an
  official spec-basis citation
- prior-informed-consent verification (transboundary-movement
  notification + destination-country competent-authority consent) for
  hazardous-waste orders, with an honest destination-country-by-
  destination-country legal-basis citation (Basel Convention Article 6,
  or the USA's own parallel RCRA import-consent regime)
- dispatch (yard/warehouse dispatch) gated on full evidence, a credit-
  cleared counterparty, contract-terms on file, verified prior informed
  consent (where the waste-stream hazard classification applies) and a
  passed sanctions screen
- invoice settlement (custody / financial transfer) with double-invoice
  prevention
- evidence checklisting (credit-clearance record, contract/PO, sanctions-
  screening record, plus transboundary-notification and destination-
  consent documentation for hazardous streams)
- informational surfacing of voluntary e-waste certification schemes
  (R2v3, e-Stewards) for a human reviewer's benefit, honestly labeled as
  non-binding
- sanctions and credit exception workflows
- role-based access and immutable audit ledger

## Revenue
- self-host setup fee
- managed hosting subscription per trader / yard / warehouse
- support retainer with SLA
- ERP and accounts-receivable integration
- destination-country consent-documentation compliance reporting add-on
  (Basel Article 6 / RCRA-aligned export of a counterparty's own
  transboundary-movement evidence trail)

## The `:waste-trading-governor` Decision Rule

This blueprint's `:itonami.blueprint/governor` is `:waste-trading-
governor`. It is the single authority that stands between "waste/scrap
could be dispatched across a border to a counterparty" and "it is
allowed to leave the wholesale yard/warehouse," and between "an invoice
could be settled" and "it is allowed to settle." Every rule it enforces
is traceable to the domain (Wholesale of Waste and Scrap and Other
Products N.E.C., ISIC 4669) and to the three `:social-impact` tags in
`blueprint.edn` (`:environmental-protection`, `:public-health`,
`:transparency`).

This is the rule the companion contract test
(`test/wastetrade/governor_contract_test.clj`) encodes end-to-end: the
WasteTradeAdvisor never dispatches waste/scrap across a border to a
counterparty or settles an invoice the Waste Trading Governor would
reject, `:delivery/dispatch` and `:invoice/settle` NEVER auto-commit at
any phase, `:order/intake` (no direct capital risk) MAY auto-commit
when clean, and every decision (commit OR hold) leaves exactly one
ledger fact.

**Authorizes a dispatch (`:delivery/dispatch`) or invoice settlement
(`:invoice/settle`) only when ALL of the following hold:**

1. **An official spec-basis citation exists for the EXPORTING
   jurisdiction** -- the governor will not authorize any `:consent/
   verify`, `:delivery/dispatch`, or `:invoice/settle` proposal whose
   jurisdiction has no entry in the `wastetrade.facts` catalog
   (`:no-spec-basis`). This is the direct enforcement of
   `:transparency`: a jurisdiction whose customs/waste-export
   requirements cannot be traced to an OFFICIAL public source is never
   guessed. The advisor must not fabricate a jurisdiction's
   requirements.
2. **The jurisdiction's required GENERAL evidence is fully on file** --
   for a dispatch or invoice the order's jurisdiction must have been
   verified with a complete counterparty-diligence evidence checklist on
   record: the credit-clearance record, the contract / purchase order,
   and the sanctions-screening (OFAC / equivalent) record
   (`:evidence-incomplete`). This is deliberately the SAME 3-item
   generic checklist every wholesale-trading sibling uses -- it does NOT
   include prior-informed-consent evidence, which is check #5 below.
3. **The counterparty's credit has been cleared** -- the governor reads
   the dedicated `:credit-cleared?` fact on the order and refuses to
   dispatch waste/scrap when credit has NOT been cleared (the leasing
   collateral-coverage discipline, applied to counterparty credit)
   (`:credit-uncleared`). Evaluated at `:delivery/dispatch`.
4. **Contract-terms are on file** -- the governor refuses to dispatch
   when no `:contract-terms` are recorded for the order
   (`:contract-missing`). Waste/scrap never leaves the yard/warehouse
   against an undocumented trade. Evaluated at `:delivery/dispatch`.
5. **For a hazardous waste-stream order, prior informed consent is
   verified** -- the governor reads the dedicated
   `:transboundary-notification-filed?` AND
   `:destination-country-consent-documented?` facts and refuses to
   dispatch a HAZARDOUS-stream order missing EITHER
   (`:prior-informed-consent-missing`). This check is a NO-OP for every
   green-list / non-hazardous waste-stream type (sorted ferrous/
   non-ferrous scrap metal, sorted single-resin plastics, sorted paper/
   cardboard, sorted clean textile waste, ...) -- it is specific to the
   Basel-Convention-style Prior Informed Consent regime, and -- UNLIKE
   every other check in this actor -- it applies UNCONDITIONALLY
   regardless of `:jurisdiction`/`:destination-country`, because real-
   world transboundary hazardous-waste movement control (the Basel
   Convention's Prior Informed Consent procedure, ratified by ~190
   Parties) is a bilateral consent requirement this actor treats as a
   universal floor for hazardous streams, not merely a where-a-Basel-
   Party-happens-to-be-involved courtesy -- even the USA (a Basel
   non-Party) has its own parallel destination-consent requirement under
   RCRA. This is the check with NO analog anywhere else in this fleet's
   wholesale-trading cluster, and the FIRST check in this fleet gated on
   a genuinely BILATERAL, government-to-government fact rather than a
   unilateral one the exporter holds -- see Implementation notes and
   `docs/adr/0001-architecture.md` Decision 4. Evaluated at `:delivery/
   dispatch`.
6. **The counterparty has passed OFAC / equivalent sanctions screening**
   -- the governor reads the dedicated `:sanctions-screened?` fact and
   treats an unresolved sanctions-screening flag as a HARD, un-
   overridable hold (`:counterparty-sanctions-flag-unresolved`). Neither
   product nor money moves against an unscreened counterparty. Evaluated
   UNCONDITIONALLY at both `:delivery/dispatch` and `:invoice/settle`.
7. **The order has not already been dispatched, and the invoice has not
   already been settled** -- a double dispatch of the same order is
   refused off a dedicated `:dispatched?` fact, and a double invoice off
   a dedicated `:invoiced?` fact (never a `:status` value), the
   double-actuation guard every sibling actor in this fleet enforces
   (`:already-dispatched` / `:already-invoiced`).

**Rejects (HOLD, un-overridable, never even reaches a human) when any of
the above fail.** A proposal with no spec-basis, incomplete evidence, an
uncleared counterparty credit, no contract-terms on file, unverified
prior informed consent on a qualifying hazardous waste-stream, an
unresolved sanctions-screening flag, or a double dispatch/invoice is
held at the governor node -- a human approver cannot override these, by
construction.

**Always escalates to a human (never auto-commits) for `:delivery/
dispatch` and `:invoice/settle`**, even when every check above is clean.
Dispatching a real waste/scrap shipment across a border to a
counterparty and settling a real waste-trade invoice (real money moving
between counterparty and trader) are the two real-world actuation events
this actor performs; both are always a human trading supervisor's call.
This is enforced by TWO independent layers that agree on purpose: the
governor's confidence / actuation SOFT gate (a `:delivery/dispatch` /
`:invoice/settle` stake always escalates) and `wastetrade.phase`'s phase
table, which never puts either op in any phase's `:auto` set. The
`:environmental-protection` and `:public-health` tags are enforced
upstream of the governor, in the consent-verification evidence step --
the governor's job is dispatch/invoice authorization integrity, not
trading-book optimization.

## Required Technologies

`blueprint.edn`'s `:itonami.blueprint/required-technologies` for this
business, and what each one is actually load-bearing for here (not a
generic capability list):

| Technology | What it is FOR in Wholesale of Waste and Scrap and Other Products N.E.C. |
|---|---|
| `:robotics` | The autonomous sorting/baling robotic system (magnetic/eddy-current separators, robotic sorting arms -- the same class of automation already commercially deployed at modern materials-recovery yards) that stages the outbound waste stream, and the autonomous loader that performs the physical outbound loadout, at the wholesaler's own yard/warehouse dispatch point. The governor never dispatches hardware itself: a dispatch-clearing action must have cleared the same sign-off a human trading supervisor would need (see Robotics Premise). |
| `:identity` | Trader, trading-supervisor, yard/warehouse-operator and counterparty identity plus role-based access, so the governor's sign-off is tied to *who* authorized a dispatch or invoice, not just *that* someone did. |
| `:forms` | Structured intake for waste-order booking, per-jurisdiction evidence capture (credit-clearance record, contract/PO, sanctions-screening record), prior-informed-consent evidence capture (transboundary-movement notification, destination-country competent-authority consent), and sanctions / credit exception submission -- the data the Decision Rule above actually evaluates comes in through these forms. |
| `:dmn` | Encodes the `:waste-trading-governor` Decision Rule itself (spec-basis, evidence completeness, credit-clearance, contract-on-file, prior-informed-consent, sanctions-screening, the double-actuation guards, the actuation gate) as an evaluable decision table rather than code buried in application logic -- this is what makes the governor auditable and swappable per-deployment. |
| `:bpmn` | Orchestrates the intake -> verify -> dispatch -> settle -> audit loop end-to-end (see `docs/operator-guide.md`) across waste-order intake, consent verification, dispatch, and invoice settlement, including the sanctions / credit escalation gate. |
| `:audit-ledger` | The immutable record of every verification, dispatch, invoice, sanctions flag, and hold -- this is what "an auditable, spec-cited, consent-cited trade record for every dispatch and invoice" (Trust Controls, below) actually means in practice, and the evidence an operator (or a destination-country competent authority) needs if a dispatch or an invoice is later disputed. |
| `:optimization` | Yard-slotting, freight-routing and trading-book optimization -- selects the profitable fulfillment strategy for a yard/warehouse. This R0 build deliberately scopes optimization OUT (see README `Business-process coverage`); the capability is correctly marked required, the integration is a follow-up slice. |

There is NO bespoke `:wastetrade` capability library in this stack
(unlike the freight sibling's `:logistics`): the waste-trading checks
(credit-clearance, contract-on-file, prior-informed-consent, sanctions-
screening) are direct entity boolean reads in `wastetrade.governor`, on
top of the generic robotics/identity/forms/dmn/bpmn/audit-ledger stack
(see Capability layer).

## Trust Controls
- a jurisdiction with no official spec-basis can never be verified,
  dispatched, or invoiced against
- a dispatch never starts with incomplete counterparty-diligence evidence
- a dispatch never starts with an uncleared counterparty credit or no
  contract-terms on file
- a hazardous-waste dispatch never starts without BOTH a filed
  transboundary-movement notification AND a documented destination-
  country competent-authority consent on file
- a dispatch or invoice never settles against an unresolved sanctions-
  screening flag
- sanctions / credit / prior-informed-consent flags cannot be silently
  suppressed
- the same order can never be dispatched or invoiced twice
- a dispatch or invoice never auto-commits; both always need a human
  trading supervisor
- every dispatch and invoice (commit OR hold) leaves exactly one
  immutable ledger fact
- counterparty, credit, sanctions and transboundary-consent data stays
  outside Git

## Implementation notes (`:implemented`)

The Decision Rule above is implemented faithfully by
`wastetrade.governor` as six HARD checks (a human approver cannot
override them) plus one SOFT gate:

- `spec-basis-violations` -- the spec-basis check above, evaluated on
  every `:consent/verify`, `:delivery/dispatch`, and `:invoice/settle`.
- `evidence-incomplete-violations` -- the GENERAL evidence-completeness
  check above, for `:delivery/dispatch` / `:invoice/settle`.
- `credit-uncleared-violations` -- the counterparty-credit check above
  (the leasing collateral-coverage discipline applied to counterparty
  credit); evaluated on every `:delivery/dispatch`.
- `contract-missing-violations` -- the contract-on-file check above;
  evaluated on every `:delivery/dispatch`.
- `prior-informed-consent-missing-violations` -- the prior-informed-
  consent check above, gated on `wastetrade.facts/hazardous-waste-
  stream?`; evaluated on every `:delivery/dispatch`, UNCONDITIONALLY
  across jurisdiction. THIS IS THE DOMAIN-DEFINING CHECK -- no analog in
  the fuel-wholesale, general-trading, commission-brokerage, agri-
  wholesale, provision-trading, metal-wholesale or textile-wholesale
  siblings' governors: it is this vertical's own defining regulatory
  content, a BILATERAL government-to-government consent rather than a
  unilateral fact the exporter holds.
- `counterparty-sanctions-flag-unresolved-violations` -- the sanctions-
  screening check above (the same open-flag-unresolved discipline the
  freight sibling's delivery-exception-unresolved check establishes);
  evaluated unconditionally on both `:delivery/dispatch` and
  `:invoice/settle`.
- `already-dispatched-violations` / `already-invoiced-violations` -- the
  double-actuation guards above, off dedicated `:dispatched?` /
  `:invoiced?` booleans (never a `:status` value), the same discipline
  every sibling governor's guards establish.
- the confidence floor / actuation SOFT gate -- low confidence, OR a
  `:delivery/dispatch` / `:invoice/settle` stake, escalates to a human;
  and `wastetrade.phase` independently never auto-commits either op at
  any phase.

Unlike the crude-extraction sibling's governor (which calls pure
physical range-check functions in its registry), this governor needs no
range-check functions at all: its domain checks read the `waste-order`
record's own dedicated booleans directly. `:delivery/dispatch` and
`:invoice/settle` are the two real-world actuation events
(`#{:delivery/dispatch :invoice/settle}`), applied SEQUENTIALLY to the
SAME waste-order (dispatch first, invoice settlement later), the same
sequential dual-actuation shape the fuel-wholesale, metal-wholesale and
textile-wholesale clusters use. Neither ever auto-commits at any phase.
Yard-slotting/freight-routing and trading-book optimization (the
`:optimization` line above) is a follow-up slice, not in this R0 build --
see README `Business-process coverage`.

## Why prior informed consent is a SEPARATE check, not folded into evidence-incomplete

The generic `evidence-incomplete-violations` check (present in every
wholesale-trading sibling) verifies the EXPORTING jurisdiction's
counterparty-diligence paperwork -- credit-clearance record,
contract/PO, sanctions-screening record -- keyed by WHERE the trade's
exporter sits. Prior informed consent is a genuinely different kind of
fact: it verifies a BILATERAL relationship between the exporting
jurisdiction and the specific `:destination-country`, and unlike every
other fact this actor or any sibling checks, it cannot be satisfied
unilaterally by the exporter at all -- the destination country's own
competent authority must affirmatively grant it. Folding the two into
one check would either force prior-informed-consent evidence to inherit
the wrong keying (jurisdiction-only, when the real fact is a
jurisdiction-PAIR relationship), or force every jurisdiction's generic
evidence checklist to grow conditional items that are irrelevant to most
(non-hazardous) orders. Keeping them as two independent checks -- one
gated by the exporting jurisdiction, one gated by the waste-stream's
hazard classification and keyed by the destination country -- lets each
check state its own gating condition honestly and lets the audit ledger
show, unambiguously, WHICH kind of gap actually blocked a dispatch.

## Capability layer

Like the fuel-wholesale (`cloud-itonami-isic-4671`), general-trading
(`cloud-itonami-isic-4690`), commission-brokerage
(`cloud-itonami-isic-4610`), agri-wholesale (`cloud-itonami-isic-4620`),
provision-trading (`cloud-itonami-isic-4630`), metal-wholesale
(`cloud-itonami-isic-4662`) and textile-wholesale
(`cloud-itonami-isic-4641`) siblings, this vertical is SELF-CONTAINED:
there is no `kotoba-lang/wastetrade` to delegate waste-trading
validation to. The credit-clearance / contract-on-file / prior-informed-
consent / sanctions-screening checks live as direct entity boolean reads
in `wastetrade.governor` (off dedicated `:credit-cleared?` /
`:contract-terms` / `:transboundary-notification-filed?` /
`:destination-country-consent-documented?` / `:sanctions-screened?`
facts on the `waste-order` record) -- this vertical's governor needs no
pure range-check functions at all, because its domain checks ARE direct
boolean reads.

## Jurisdiction coverage (honest)

`wastetrade.facts/catalog` currently seeds 4 EXPORTING jurisdictions
with an official GENERAL (customs/waste-export) spec-basis, each a REAL
regime:

- **Japan (JPN)** -- the Waste Management and Public Cleansing Act
  (廃棄物の処理及び清掃に関する法律) and the Act on the Control of Export,
  Import and Others of Specified Hazardous Wastes and Other Wastes
  (特定有害廃棄物等の輸出入等の規制に関する法律, Japan's Basel-implementing
  statute, commonly referred to as the "Basel Act" / バーゼル法),
  administered by 環境省 (the Ministry of the Environment) with customs
  administered by 財務省 (MOF) / 経済産業省 (METI). I am highly confident
  Japan has implemented the Basel Convention domestically through a
  dedicated statute of this name and general character; I am only
  moderately confident about the precise current article-level citation
  boundaries and should be independently verified.
- **United States (USA)** -- the Resource Conservation and Recovery Act
  (RCRA, 42 U.S.C. §6901 et seq.), with hazardous-waste export/import
  requirements at 40 CFR Part 262 Subpart H, administered by the
  Environmental Protection Agency (EPA), plus U.S. Customs and Border
  Protection (CBP) entry and OFAC (Treasury) sanctions programs. I am
  highly confident about RCRA's role as the US hazardous-waste statute
  and EPA's administering role; I am reasonably, but not fully,
  confident about the precise current CFR part/subpart citation and this
  should be independently verified.
- **United Kingdom (GBR)** -- the Transfrontier Shipment of Waste
  Regulations 2007 (SI 2007/1711, as amended), administered by the
  Environment Agency (England) / DEFRA. I am reasonably confident this
  is the correct UK statutory instrument implementing international
  waste-shipment control post-Brexit (as retained/amended EU-derived
  law), but I have not independently verified every subsequent amendment
  and this should be checked before operational reliance.
- **Germany (DEU)**, representing the EU regime -- Regulation (EU)
  2024/1157 on shipments of waste, the RECAST EU Waste Shipment
  Regulation that replaces the older Regulation (EC) No 1013/2006,
  administered nationally by the Umweltbundesamt (German Environment
  Agency) and German Customs (Generalzolldirektion) under the
  Bundesministerium der Finanzen (BMF). I am confident Regulation (EU)
  2024/1157 is a real, adopted recast of the EU Waste Shipment
  Regulation; I am NOT fully confident about the precise provision-by-
  provision phased-application timeline, so if you are relying on this
  operationally, verify which specific provisions are currently in force
  as of your read date rather than assuming the whole regulation is
  uniformly applicable.

`wastetrade.facts/consent-basis` seeds a prior-informed-consent regime
for ALL 4 of the jurisdictions above -- a DELIBERATE difference from the
metal-wholesale sibling's `conflict-minerals-basis` (which has a binding
statute for only 2 of its 4 seeded jurisdictions and a non-statutory
OECD-Guidance fallback for the rest): every destination seeded here has
a genuinely BINDING consent-from-destination-authority regime, because
this is what the Basel Convention's near-universal (~190 Parties)
ratification actually looks like in practice:

- **Japan, United Kingdom, Germany (representing the EU)** -- all three
  are Parties to the Basel Convention on the Control of Transboundary
  Movements of Hazardous Wastes and Their Disposal (adopted 22 March
  1989, entered into force 5 May 1992), and the citation is the
  Convention's own Article 6 Prior Informed Consent procedure (written
  notification to, and written consent from, the importing State's
  competent authority BEFORE a hazardous-waste export proceeds). I am
  highly confident about the Basel Convention's existence, adoption/
  entry-into-force dates, near-universal ratification, and the core
  shape of the Article 6 PIC procedure; I have not independently
  verified each of these three countries' CURRENT Basel Convention
  National Competent Authority / Focal Point designee by name, and that
  detail should be checked before operational reliance.
- **United States** -- the USA is a well-documented, frequently-cited
  NON-Party to the Basel Convention: it signed the Convention in 1990,
  but the U.S. Senate has never given advice and consent to ratify it.
  I am highly confident about this fact. This does NOT mean a
  USA-destination hazardous-waste shipment has no consent-from-
  destination requirement at all -- RCRA imposes its own PARALLEL
  requirement: 40 CFR §262.83 requires the exporter to notify EPA before
  export (I am reasonably confident about this citation), and 40 CFR
  §262.84 requires EPA's own consent before a hazardous-waste import
  into the USA proceeds (I am reasonably, but not fully, confident about
  this specific citation and the precise interaction with the OECD
  Council Decision C(2001)107/FINAL for OECD-country-to-OECD-country
  recovery-bound shipments -- both should be independently verified
  before this catalog is relied on operationally).

## Why R2v3 and e-Stewards are surfaced but never gate a HARD check

For WEEE/e-waste orders specifically, `wastetrade.facts/e-waste-
certification-schemes` surfaces two well-known, third-party-audited,
PRIVATE, VOLUNTARY electronics-recycling certification schemes -- R2v3
(Responsible Recycling, administered by Sustainable Electronics
Recycling International / SERI) and e-Stewards (administered by the
Basel Action Network / BAN). I am confident both schemes are real and
widely referenced in the electronics-recycling industry. I am
DELIBERATELY NOT treating either as a legal requirement this actor
enforces: neither is a government statute, and presenting either as if
it were would misrepresent a private certification as binding law --
the SAME honest distinction `wastetrade.facts`'s `consent-basis`
catalog draws between `:binding? true` government law and any
non-statutory baseline. Some downstream buyers or sub-national
procurement rules require one or the other as a matter of contract or
policy, not universal statute, so both are surfaced only as an
INFORMATIONAL citation `wastetradeadvisor` may draft for a human
reviewer's benefit on an e-waste order -- never a HARD governor check.

## Maturity

`:implemented` -- `WasteTradeAdvisor` + `Waste Trading Governor` run as
real, tested code (`clojure -M:dev:test`: 46 tests / 228 assertions, 0
failures; lint clean), following the SAME governed-actor architecture as
the other prior actors across this fleet, with its own distinct,
independently-named governor and its own direct-entity-boolean waste-
trading checks -- including the fleet's first BILATERAL,
government-to-government-consent-gated domain-defining check. See
`docs/adr/0001-architecture.md` for the history and design.

## Robotics Premise

`blueprint.edn` sets `:itonami.blueprint/robotics true`. This is a
reasoned call, not a default carried over from a sibling: real modern
waste/scrap-wholesale yards already run substantial physical automation
at the sorting/dispatch point -- AI-driven robotic sorting arms, magnetic
and eddy-current separators, and automated baling systems are
commercially deployed at materials-recovery facilities and scrap yards
today (a well-documented, real automation category, not a speculative
claim). An autonomous sorting/baling robotic system stages the outbound
waste stream, and an autonomous loader performs the physical outbound
loadout, at the wholesale yard/warehouse -- the point at which this
actor's `:delivery/dispatch` occurs -- under the actor, gated by the
independent Waste Trading Governor.

This claim is deliberately scoped to THIS actor's own wholesale-dispatch
act, not the DOWNSTREAM recycling/shredding/smelting/disposal processing
that happens after a shipment reaches its destination -- that processing
is a separate ISIC activity (materials-recovery/recycling manufacturing,
e.g. ISIC 3830 or 24xx), out of scope for this wholesale-trading R0 (see
README Scope). The governor never dispatches hardware itself: a
dispatch-clearing action must have cleared the same sign-off a human
trading supervisor would need. A robot may sort or stage a waste stream,
but only after the governor (every HARD check clean) and a human
supervisor both agree it is safe to dispatch -- the same operating-
state-machine-gated-by-governor premise every cloud-itonami vertical
restates (ADR-2607011000): the blueprint declares `:robotics true`, the
README names the robot that performs the physical act, and the Waste
Trading Governor is the independent gate that robot's command must pass.
