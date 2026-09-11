(ns wastetrade.governor
  "Waste Trading Governor -- the independent compliance layer that earns
  the WasteTradeAdvisor the right to commit. The LLM has no notion of
  jurisdictional customs/export-control law, whether a counterparty's
  credit has actually been cleared, whether contract terms are actually
  on file, whether a REAL transboundary-movement notification has
  actually been filed AND a REAL destination-country competent-authority
  consent actually exists for THIS waste-order, whether OFAC / equivalent
  sanctions screening has actually been passed, or when an act stops
  being a draft and becomes a real dispatch of waste/scrap or a real
  invoice settlement, so this MUST be a separate system able to *reject*
  a proposal and fall back to HOLD.

  Like the metal-wholesale and textile-wholesale siblings' own
  governors, this waste/scrap-wholesale vertical has NO pre-existing
  waste-trading capability library to delegate to -- so the domain
  checks (credit-clearance, contract-on-file, prior-informed-consent,
  sanctions-screening) are direct entity boolean reads off the
  `waste-order` record, evaluated directly here, NOT delegated to a
  separate library's validated function.

  `:itonami.blueprint/governor` is `:waste-trading-governor`, grep-
  verified UNIQUE fleet-wide -- no naming-collision precedent question,
  a fresh independent build following the SAME governed-actor
  architecture (langgraph StateGraph + independent Governor + Phase
  0->3 rollout) established by `cloud-itonami-isic-6511` and applied by
  the fuel-wholesale (`cloud-itonami-isic-4671`), general-trading
  (`cloud-itonami-isic-4690`), commission-brokerage
  (`cloud-itonami-isic-4610`), agri-wholesale (`cloud-itonami-isic-4620`),
  provision-trading (`cloud-itonami-isic-4630`), metal-wholesale
  (`cloud-itonami-isic-4662`) and textile-wholesale
  (`cloud-itonami-isic-4641`) siblings.

  CRITICAL STRUCTURAL DIFFERENCE from EVERY prior sibling in this fleet's
  wholesale-trading cluster: this vertical's core regulated event is not
  'is this trade/commodity/counterparty compliant' at all -- it is
  whether a shipment of waste is allowed to physically cross a border in
  the first place, and whether it carries the paper trail the
  DESTINATION country's own environmental regulator requires BEFORE the
  shipment is allowed to move. This is an ENVIRONMENTAL transboundary-
  movement regime (the Basel Convention's Prior Informed Consent
  procedure), not a trade-sanctions, biosecurity, food-safety or
  human-rights regime, and it is GENUINELY BILATERAL: unlike every prior
  sibling's checks (each a unilateral fact the EXPORTER holds --
  credit-clearance, contract-on-file, chain-of-custody documentation,
  sanctions screening), Prior Informed Consent is a government-to-
  government act the DESTINATION country's own competent authority must
  affirmatively grant.

  `prior-informed-consent-missing-violations` is gated on the order's
  OWN `:waste-stream-type` hazard classification alone
  (`wastetrade.facts/hazardous-waste-stream?`), evaluated
  UNCONDITIONALLY across every jurisdiction/destination-country pairing
  -- the SAME gating shape the metal-wholesale sibling's conflict-
  minerals check uses (Option C in that sibling's Decision 4), and a
  deliberate DEPARTURE from the textile-wholesale sibling's
  jurisdiction-gated forced-labor check. See this check's own docstring
  below and `docs/adr/0001-architecture.md` Decision 4 for the full
  three-way contrast and the reasoning for why Basel PIC's near-universal
  (~190 Parties) multilateral ratification -- and the fact that even the
  one major non-Party seeded here (the USA) has its OWN parallel
  destination-consent requirement under RCRA -- makes it structurally
  closer to the metal-wholesale sibling's shape than the textile-
  wholesale sibling's single-country-border-mechanism shape.

  Seven checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them. The confidence/actuation gate is SOFT: it asks
  a human to look (low confidence / actuation), and the human may
  approve -- but see `wastetrade.phase`: for `:stake
  :delivery/dispatch`/`:invoice/settle` (a real dispatch or invoice
  settlement) NO phase ever allows auto-commit either. Two independent
  layers agree that actuation is always a human call.

    1. Spec-basis                  -- did the jurisdiction proposal cite
                                       an OFFICIAL source
                                       (`wastetrade.facts`), or invent
                                       one?
    2. Evidence incomplete         -- for `:delivery/dispatch`/
                                       `:invoice/settle`, has the
                                       jurisdiction actually been
                                       verified with a full counterparty-
                                       diligence evidence checklist on
                                       file?
    3. Credit uncleared            -- for `:delivery/dispatch`, the
                                       counterparty's credit has NOT been
                                       cleared. Evaluated before dispatch.
    4. Contract missing            -- for `:delivery/dispatch`, no
                                       contract-terms are on file for the
                                       order. Evaluated before dispatch.
    5. Prior informed consent
       missing                       -- for `:delivery/dispatch`, WHEN
                                       `:waste-stream-type` is classified
                                       HAZARDOUS
                                       (`wastetrade.facts/hazardous-
                                       waste-stream?`), the waste-order
                                       lacks a filed transboundary-
                                       movement notification OR lacks a
                                       documented destination-country
                                       competent-authority consent (or
                                       both). THIS IS THE DOMAIN-DEFINING
                                       CHECK -- it has no analog in ANY
                                       prior wholesale-trading sibling's
                                       governor: every other sibling's
                                       provenance/diligence facts are
                                       something the EXPORTER holds or
                                       obtains unilaterally; this one is
                                       a genuinely BILATERAL
                                       government-to-government consent
                                       the DESTINATION country must
                                       affirmatively grant. NO-OP for
                                       every green-list / non-hazardous
                                       waste-stream type. Evaluated
                                       before dispatch, UNCONDITIONALLY
                                       across every jurisdiction/
                                       destination-country pairing (see
                                       namespace docstring).
    6. Counterparty sanctions flag
       unresolved                    -- for `:delivery/dispatch` and
                                       `:invoice/settle`, the counterparty
                                       has NOT passed OFAC / equivalent
                                       sanctions screening -- a HARD,
                                       un-overridable hold. Evaluated
                                       UNCONDITIONALLY at both actuation
                                       ops.
    7. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:delivery/dispatch`/
                                       `:invoice/settle` (REAL acts)
                                       -> escalate.

  Two more guards, double-dispatch/double-invoice prevention, are
  enforced but NOT listed as numbered HARD checks above because they
  need no upstream comparison at all -- `already-dispatched-violations`/
  `already-invoiced-violations` refuse to dispatch/invoice the SAME
  waste-order twice, off dedicated `:dispatched?`/`:invoiced?` facts
  (never a `:status` value) -- the SAME 'check a dedicated boolean, not
  status' discipline every prior governor's guards establish, informed
  by `cloud-itonami-isic-6492`'s status-lifecycle bug (ADR-2607071320)."
  (:require [wastetrade.facts :as facts]
            [wastetrade.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Dispatching a real waste/scrap shipment across a border to a
  counterparty and settling a real waste-trade invoice (real money
  moving between counterparty and trader) are the two real-world
  actuation events this actor performs -- a two-member set, matching
  every sibling's own dual-actuation shape."
  #{:delivery/dispatch :invoice/settle})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:consent/verify` (or `:delivery/dispatch`/`:invoice/settle`)
  proposal with no spec-basis citation is a HARD violation -- never
  invent a jurisdiction's customs/export-control requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:consent/verify :delivery/dispatch :invoice/settle} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:delivery/dispatch`/`:invoice/settle`, the jurisdiction's
  required GENERAL counterparty-diligence evidence (credit-clearance
  record, contract/PO, sanctions-screening record) must actually be
  satisfied -- do not trust the advisor's self-reported confidence
  alone. Deliberately does NOT check prior-informed-consent evidence --
  that is `prior-informed-consent-missing-violations` below, gated on
  the order's own waste-stream hazard classification rather than the
  generic per-jurisdiction checklist."
  [{:keys [op subject]} st]
  (when (contains? #{:delivery/dispatch :invoice/settle} op)
    (let [wo (store/waste-order st subject)
          assessment (store/assessment-of st subject)]
      (when-not (and assessment
                     (facts/required-evidence-satisfied?
                      (:jurisdiction wo) (:checklist assessment)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(信用審査記録/契約書またはPO/制裁スクリーニング記録)が充足していない状態での提案"}]))))

(defn- credit-uncleared-violations
  "For `:delivery/dispatch`, refuses to dispatch waste/scrap to a
  counterparty whose credit has NOT been cleared -- counterparty credit
  not cleared (the leasing collateral-coverage discipline, applied to
  counterparty credit). Evaluated ahead of any physical loadout."
  [{:keys [op subject]} st]
  (when (= op :delivery/dispatch)
    (let [wo (store/waste-order st subject)]
      (when (not (true? (:credit-cleared? wo)))
        [{:rule :credit-uncleared
          :detail (str subject " の取引先信用審査(credit-clearance)が未了 -- 出荷提案は進められない")}]))))

(defn- contract-missing-violations
  "For `:delivery/dispatch`, refuses to dispatch waste/scrap when no
  contract-terms are on file for the order."
  [{:keys [op subject]} st]
  (when (= op :delivery/dispatch)
    (let [wo (store/waste-order st subject)]
      (when (or (nil? (:contract-terms wo)) (= "" (:contract-terms wo)))
        [{:rule :contract-missing
          :detail (str subject " に契約条項(contract-terms)の記録が無い -- 出荷提案は進められない")}]))))

(defn- prior-informed-consent-missing-violations
  "For `:delivery/dispatch`, WHEN `:waste-stream-type` is classified
  HAZARDOUS (`wastetrade.facts/hazardous-waste-stream?`), refuses to
  dispatch UNLESS BOTH a filed transboundary-movement notification
  (`:transboundary-notification-filed?`, the Basel Article 6(1) /
  RCRA §262.83 exporter notification to the destination country's
  competent authority) AND a documented destination-country competent-
  authority consent (`:destination-country-consent-documented?`, the
  Basel Article 6(2)/(3) written consent / RCRA §262.84 EPA consent to
  import) are on file. Folds these TWO distinct real-world
  sub-requirements into ONE named rule -- the SAME discipline the
  metal-wholesale sibling's `conflict-minerals-provenance-unverified-
  violations` and the textile-wholesale sibling's `forced-labor-
  presumption-unrebutted-violations` checks establish (see
  `docs/adr/0001-architecture.md` Decision 4): a filed notification
  without the destination's own consent (or vice versa) is EQUALLY
  unsafe to dispatch against, because both are arms of the SAME
  real-world Prior Informed Consent procedure (Basel Article 6 treats
  notification and consent as two sequential steps of ONE bilateral
  act, not two independent regimes) -- the `:detail` string still names
  which sub-fact specifically failed, so no audit-ledger precision is
  lost.

  THIS CHECK HAS NO ANALOG ANYWHERE ELSE IN THIS FLEET'S WHOLESALE-
  TRADING CLUSTER: every other sibling's domain-defining check re-
  verifies a fact the EXPORTER itself holds or unilaterally obtains
  (chain-of-custody documentation, a rebuttal evidence dossier, a
  phytosanitary certificate, a food-safety certificate). Prior Informed
  Consent is different in KIND -- it is a bilateral government-to-
  government act: the DESTINATION country's own competent authority
  must affirmatively grant consent BEFORE the shipment proceeds. This
  actor cannot manufacture that consent; it can only refuse to dispatch
  until the order's own record shows it genuinely exists.

  This is a NO-OP for every green-list / non-hazardous waste-stream type
  (sorted ferrous/non-ferrous scrap metal, sorted single-resin plastics,
  sorted paper/cardboard, sorted clean textile waste, ...) -- `wo-7` in
  the demo data (`wastetrade.store/demo-data`) proves this directly: a
  sorted ferrous-scrap order with BOTH sub-facts false still dispatches
  cleanly, because sorted ferrous scrap carries no hazardous designation
  in this actor's scope and genuinely moves under the Basel Convention's
  lighter Annex IX / OECD 'green list' regime -- see
  `wastetrade.facts/green-list-waste-streams`.

  UNCONDITIONAL ACROSS JURISDICTION (unlike the textile-wholesale
  sibling's jurisdiction-gated forced-labor check): this check fires for
  a hazardous waste-order regardless of `:jurisdiction`/
  `:destination-country`, including when the destination is the USA (a
  Basel non-Party) -- `wo-6` in the demo data proves this: a
  hazardous-stream order destined for the USA with BOTH sub-facts false
  HOLDS exactly like one destined for a Basel Party, because the USA has
  its OWN parallel, genuinely binding destination-consent requirement
  under RCRA (`wastetrade.facts/consent-basis` 'USA' entry) -- the
  CITATION shown to a human reviewer differs (Basel Article 6 PIC vs.
  RCRA import-consent), but the CHECK does not. See namespace docstring
  and `docs/adr/0001-architecture.md` Decision 4 for the full reasoning
  on why this gating shape was chosen over a jurisdiction-gated
  alternative."
  [{:keys [op subject]} st]
  (when (= op :delivery/dispatch)
    (let [wo (store/waste-order st subject)]
      (when (and (facts/hazardous-waste-stream? (:waste-stream-type wo))
                 (not (and (true? (:transboundary-notification-filed? wo))
                           (true? (:destination-country-consent-documented? wo)))))
        [{:rule :prior-informed-consent-missing
          :detail (str subject " (" (:waste-stream-type wo) ", 有害廃棄物該当, 仕向国="
                       (:destination-country wo) ")の国境を越える移動事前通報(transboundary-"
                       "notification)="
                       (boolean (:transboundary-notification-filed? wo))
                       " / 仕向国の権限ある当局の事前同意(destination-country consent)="
                       (boolean (:destination-country-consent-documented? wo))
                       " -- いずれかが未充足のため出荷提案は進められない")}]))))

(defn- counterparty-sanctions-flag-unresolved-violations
  "For `:delivery/dispatch` and `:invoice/settle`, an unresolved
  sanctions-screening flag -- the counterparty has NOT passed OFAC /
  equivalent sanctions screening -- is a HARD, un-overridable hold.
  Evaluated UNCONDITIONALLY at both actuation ops: neither waste/scrap
  nor money moves against an unscreened counterparty."
  [{:keys [op subject]} st]
  (when (contains? #{:delivery/dispatch :invoice/settle} op)
    (let [wo (store/waste-order st subject)]
      (when (not (true? (:sanctions-screened? wo)))
        [{:rule :counterparty-sanctions-flag-unresolved
          :detail (str subject " の取引先制裁スクリーニング(OFAC等)が未了 -- 出荷・請求提案は進められない")}]))))

(defn- already-dispatched-violations
  "For `:delivery/dispatch`, refuses to dispatch the SAME waste-order
  twice, off a dedicated `:dispatched?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :delivery/dispatch)
    (when (store/waste-order-already-dispatched? st subject)
      [{:rule :already-dispatched
        :detail (str subject " は既に出荷済み")}])))

(defn- already-invoiced-violations
  "For `:invoice/settle`, refuses to settle the SAME waste-order's
  invoice twice, off a dedicated `:invoiced?` fact (never a `:status`
  value)."
  [{:keys [op subject]} st]
  (when (= op :invoice/settle)
    (when (store/waste-order-already-invoiced? st subject)
      [{:rule :already-invoiced
        :detail (str subject " は既に請求済み")}])))

(defn check
  "Censors a WasteTradeAdvisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (credit-uncleared-violations request st)
                           (contract-missing-violations request st)
                           (prior-informed-consent-missing-violations request st)
                           (counterparty-sanctions-flag-unresolved-violations request st)
                           (already-dispatched-violations request st)
                           (already-invoiced-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
