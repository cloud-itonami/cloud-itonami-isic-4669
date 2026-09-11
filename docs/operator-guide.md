# Operator Guide

## First Deployment
1. Register traders, yards/warehouses, waste-orders, and trading
   supervisors.
2. Import waste-order, counterparty, credit, sanctions and
   transboundary-consent history.
3. Seed the per-EXPORTING-jurisdiction spec-basis catalog
   (`wastetrade.facts`) for the jurisdictions you actually trade from,
   citing real official sources only. Seed `wastetrade.facts/consent-
   basis` for every DESTINATION country you actually ship to -- Basel
   Convention Article 6 for a Party, the destination's own parallel
   import-consent regime (like RCRA for the USA) for a non-Party. There
   is no non-statutory fallback here (unlike the metal-wholesale
   sibling's OECD Guidance baseline): if a destination has no seeded
   consent-basis, the advisor honestly reports none, and you must add a
   real citation before dispatching hazardous waste there.
4. Confirm which waste-stream types your business trades are in
   `wastetrade.facts/hazardous-waste-streams` vs.
   `wastetrade.facts/green-list-waste-streams`; extend either set only
   with a documented real-world citation (a real Basel Annex VIII/List-A
   entry, or an equivalent domestic hazardous-waste classification) --
   never add a stream type "to be safe" without one.
5. Run read-only spec-basis validation per jurisdiction.
6. Configure sanctions / credit escalation and accounts-receivable
   accounts.
7. Publish a dry-run dispatch/invoice and audit export.

## Minimum Trading Controls
- spec-basis validation before any verification, dispatch, or invoice
- full counterparty-diligence evidence (credit-clearance record,
  contract/PO, sanctions-screening record) before any dispatch
- for hazardous-waste-stream orders: a genuinely FILED transboundary-
  movement notification AND a genuinely DOCUMENTED destination-country
  competent-authority consent before any dispatch -- never a paperwork
  formality, and never assumed just because the destination is a Basel
  Party
- credit-clearance, contract-on-file and prior-informed-consent checks
  before any dispatch; sanctions-screening before any dispatch AND any
  invoice
- sanctions / credit escalation gate
- audit export for every dispatch, invoice, and hold
- backup manual dispatch and invoicing process

## A Day in the Life: Intake → Verify → Dispatch → Settle → Audit

Wholesale of Waste and Scrap and Other Products N.E.C. (ISIC 4669,
`cloud-itonami-isic-4669`) runs on the same intake / advise / govern /
decide / commit-or-hold loop as every itonami blueprint, but here the
loop is concrete: a regional waste/scrap trader needs to bring a
waste-order (say, a WEEE/e-waste shipment from a Japan-based trader to a
German counterparty) from intake through prior-informed-consent
verification to a waste/scrap dispatch and an invoice settlement.
Walking through one order, end to end:

1. **Intake.** The trader books the waste-order through `:forms`:
   order-id, waste-stream-type, counterparty, price, contract-terms,
   jurisdiction (the trader's OWN exporting jurisdiction),
   destination-country (the IMPORTING country), and the order's own
   diligence record (credit-cleared?, sanctions-screened?,
   transboundary-notification-filed?, destination-country-consent-
   documented?). This creates a waste-order record at `:order/intake`
   status. The WasteTradeAdvisor only normalizes the patch; it does not
   invent the order-id, counterparty, waste-stream-type,
   destination-country, or any commercial/diligence value.
2. **Verify.** The WasteTradeAdvisor drafts a per-EXPORTING-jurisdiction
   contract / sanctions evidence checklist (`:consent/verify`) from
   `wastetrade.facts`, citing the jurisdiction's official spec-basis
   (owner authority, legal basis, provenance) and listing the required
   evidence (credit-clearance record, contract/PO, sanctions-screening
   record). WHEN the order's waste-stream-type is hazardous, it ALSO
   drafts an informational prior-informed-consent citation (Basel
   Convention Article 6, or the destination's own parallel import-
   consent regime, per `wastetrade.facts/consent-citation` keyed by the
   order's OWN `:destination-country`) -- this citation is for the human
   reviewer's benefit only; it does NOT substitute for the order's own
   transboundary-notification/destination-consent facts, which the
   governor re-verifies independently at dispatch. WHEN the order is a
   WEEE/e-waste stream, it ALSO surfaces the private, voluntary R2v3/
   e-Stewards certification schemes as an informational note -- never a
   legal requirement. The `:waste-trading-governor` sign-off gate must
   clear: it checks the jurisdiction actually has an official spec-basis
   on file (never invent one). A jurisdiction with no spec-basis is a
   HARD hold at the governor node -- it never even reaches a human. This
   verification always escalates to a human for approval; it is never
   auto.
3. **Dispatch.** Before waste/scrap can leave the yard/warehouse and
   cross a border, the `:waste-trading-governor` sign-off gate runs the
   full HARD check set against the order's own ground truth: the
   spec-basis exists, the evidence checklist is complete, the
   counterparty's credit has been cleared, contract-terms are on file --
   WHEN the waste-stream-type is hazardous -- a filed transboundary-
   movement notification AND a documented destination-country
   competent-authority consent are BOTH on file, the counterparty has
   passed sanctions screening, and the order has not already been
   dispatched. Any failure is a HARD hold that a human cannot override.
   If every check is clean, the proposal STILL always escalates to a
   human trading supervisor -- a `:delivery/dispatch` never auto-commits
   at any phase. On approval, the dispatch record is drafted
   (`<JURISDICTION>-DISPATCH-000001`) and the order's `:dispatched?`
   flag is set.
4. **Settle.** Once waste/scrap has actually been dispatched, the
   invoice is settled (`:invoice/settle`): the money side of the trade,
   custody / financial transfer. The governor re-checks the spec-basis,
   the evidence completeness, the sanctions screening, and that this
   order's invoice has not already been settled. As with the dispatch, a
   clean invoice STILL always escalates to a human trading supervisor --
   `:invoice/settle` never auto-commits. On approval the invoice record
   is drafted (`<JURISDICTION>-INVOICE-000001`) and the order's
   `:invoiced?` flag is set.
5. **Audit.** The verification, the dispatch sign-off, the dispatch
   record, the invoice sign-off, and the invoice record are all appended
   to the `:audit-ledger` -- immutable and exportable, so a counterparty,
   a destination-country competent authority, or a regulatory dispute can
   be traced back to the exact spec-basis citation, evidence checklist,
   prior-informed-consent evidence (where applicable), and supervisor
   sign-off that authorized the dispatch and invoice. If something is
   wrong with the counterparty or the transboundary paperwork (a credit
   deterioration, a sanctions hit, a contract gap, a missing destination-
   country consent), that gets raised as a flag and routed through the
   escalation gate instead of being silently suppressed -- a dispatch for
   that order then waits on governor sign-off of the flag's resolution.

Any deviation from this loop is exactly what the Trust Controls in
`docs/business-model.md` exist to catch: an order verified against a
fabricated spec-basis, a dispatch started with incomplete evidence, an
uncleared counterparty credit or a contract gap, a hazardous-waste
dispatch started without a genuinely filed notification or documented
destination-country consent, a sanctions screening suppressed to force
a dispatch through, or an invoice posted without a human sign-off.

## Feel the Decision Gate: `kbb -M:dev:run`

This vertical has no companion playable prototype. The fastest hands-on
way to feel why the `:waste-trading-governor` gate exists is the bundled
demo, which walks one clean hazardous waste-order through intake →
verify → dispatch → settle (each dispatch/settle pausing for human
approval) and then exercises every HARD-hold failure mode in isolation,
PLUS the control case that proves the prior-informed-consent check is
genuinely hazard-type-gated:

- a jurisdiction with no official spec-basis → HOLD (`:no-spec-basis`),
- a counterparty whose credit has not been cleared → HOLD
  (`:credit-uncleared`),
- an order with no contract-terms on file → HOLD (`:contract-missing`),
- a counterparty that has not passed sanctions screening → HOLD
  (`:counterparty-sanctions-flag-unresolved`),
- a hazardous waste-stream order (used lead-acid batteries) destined for
  the USA -- a Basel non-Party, but with its own parallel RCRA
  destination-consent requirement -- with NEITHER a filed transboundary
  notification NOR a documented destination-country consent → HOLD
  (`:prior-informed-consent-missing`) -- the domain-defining check,
- the SAME undocumented facts on a sorted-ferrous-scrap order (not a
  hazardous stream) → dispatches CLEANLY, only the ordinary human
  sign-off gate applies -- proving the check is hazard-type-gated, not a
  blanket transboundary-movement requirement,
- a hazardous chemical-waste order with the notification FILED but the
  destination-country consent still undocumented → still HOLD -- proving
  BOTH sub-facts are required, not either,
- a double dispatch of the same order → HOLD (`:already-dispatched`),
- a double invoice of the same order → HOLD (`:already-invoiced`).

Each HOLD settles at the governor node and never reaches a human
approver -- the same failure mode the audit ledger is built to catch and
the minimum trading controls above are built to prevent. It is not a
substitute for those controls, but it is the fastest way for a new
operator (or a reviewer) to feel, hands-on, why the gate exists before
touching a real deployment.

## Certification
Certified operators must prove spec-basis-grounded verification,
evidence-backed dispatch readiness (credit-clearance, contract-on-file,
sanctions-screening), GENUINE prior-informed-consent verification
(filed transboundary notification AND documented destination-country
competent-authority consent) for every hazardous-waste order (never a
paperwork formality), and human review for every dispatch- and invoice-
affecting action.
