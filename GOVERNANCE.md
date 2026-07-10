# Governance

`cloud-itonami-isic-4669` is an OSS open-business blueprint for wholesale
of waste and scrap and other products n.e.c.

## Maintainers
Maintainers may merge changes that preserve these invariants:
- a waste-order with no official spec-basis can never be verified,
  dispatched, or invoiced against.
- the Waste Trading Governor remains independent of the advisor.
- hard policy violations (spec-basis fabrication, evidence-suppression,
  prior-informed-consent suppression, forced dispatch/settlement) cannot
  be overridden by human approval.
- `:delivery/dispatch` and `:invoice/settle` never auto-commit at any phase.
- every dispatch, invoice, consent verification and hold is auditable.
- counterparty, credit, sanctions and transboundary-consent data stays
  outside Git.

## Decision Records
Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, prior-informed-consent check scope, public business
model, operator certification or license should add or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification is a
separate trust mark and should require security, audit and data-flow review.

Certified operators can lose certification for:
- bypassing consent-verification, dispatch or settlement policy checks
- dispatching hazardous waste without a genuinely documented transboundary
  notification and destination-country competent-authority consent on file
- mishandling counterparty or destination-consent data
- misrepresenting certification status
- failing to respond to security incidents
