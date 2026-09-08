(ns wastetrade.registry
  "Pure-function waste-dispatch + waste-invoice record construction -- an
  append-only waste/scrap-wholesale book-of-record draft.

  Like the metal-wholesale and textile-wholesale siblings' own
  registries, this waste/scrap-wholesale vertical's Waste Trading
  Governor needs NO registry range-check functions at all: its domain
  checks (credit-uncleared, contract-missing,
  prior-informed-consent-missing, counterparty-sanctions-flag-
  unresolved) are direct entity boolean reads in `wastetrade.governor`,
  off dedicated `:credit-cleared?` / `:contract-terms` /
  `:transboundary-notification-filed?` /
  `:destination-country-consent-documented?` / `:sanctions-screened?`
  facts on the `waste-order` record. So this namespace is RECORD
  CONSTRUCTION ONLY -- no pure range checks to host here.

  Like every sibling actor's registry, there is no single international
  reference-number standard for a waste-dispatch or waste-invoice
  record -- every operator/jurisdiction assigns its own reference
  format. This namespace does NOT invent one beyond a jurisdiction-
  scoped sequence number; it validates the record's required fields,
  the same honest, non-fabricating discipline `wastetrade.facts` uses.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real weighbridge/customs/ERP/billing system, and
  critically NO network call to any real Basel Convention competent-
  authority PIC system. It builds the RECORD an operator would keep, not
  the act of dispatching real waste/scrap across a border or settling a
  real invoice itself (that is `wastetrade.operation`'s `:delivery/
  dispatch`/`:invoice/settle`, always human-gated -- see README
  `Actuation`)."
  (:require [kotoba.lang.text :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is
  the operator's act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

;; ----------------------------- record construction -----------------------------

(defn register-dispatch-record
  "Validate + construct the WASTE-DISPATCH registration DRAFT -- the
  operator's own legal act of dispatching a real waste/scrap shipment
  across a border to a counterparty. Pure function -- does not touch any
  real weighbridge/customs/ERP system, and critically does NOT contact
  any real Basel Convention competent-authority PIC system; it builds
  the RECORD an operator would keep. `wastetrade.governor` independently
  re-verifies the counterparty's credit-clearance, contract-on-file,
  prior-informed-consent (where applicable) and sanctions-screening
  ground truth, and blocks a double-dispatch of the same waste-order,
  before this is ever allowed to commit."
  [waste-order-id jurisdiction sequence]
  (when-not (and waste-order-id (not= waste-order-id ""))
    (throw (ex-info "waste-dispatch: waste_order_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "waste-dispatch: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "waste-dispatch: sequence must be >= 0" {})))
  (let [dispatch-number (str (str/upper jurisdiction) "-DISPATCH-" (zero-pad sequence 6))
        record {"record_id" dispatch-number
                "kind" "waste-dispatch-draft"
                "waste_order_id" waste-order-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "dispatch_number" dispatch-number
     "certificate" (unsigned-certificate "WasteDispatch" dispatch-number dispatch-number)}))

(defn register-invoice-record
  "Validate + construct the WASTE-INVOICE registration DRAFT -- the
  operator's own legal act of settling a real waste-trade invoice (the
  money side of the trade, custody/financial transfer). Pure function --
  does not touch any real billing or accounts-receivable system; it
  builds the RECORD an operator would keep. `wastetrade.governor`
  independently re-verifies the sanctions-screening and evidence-
  completeness ground truth, and blocks a double-invoice of the same
  waste-order, before this is ever allowed to commit."
  [waste-order-id jurisdiction sequence]
  (when-not (and waste-order-id (not= waste-order-id ""))
    (throw (ex-info "waste-invoice: waste_order_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "waste-invoice: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "waste-invoice: sequence must be >= 0" {})))
  (let [invoice-number (str (str/upper jurisdiction) "-INVOICE-" (zero-pad sequence 6))
        record {"record_id" invoice-number
                "kind" "waste-invoice-draft"
                "waste_order_id" waste-order-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "invoice_number" invoice-number
     "certificate" (unsigned-certificate "WasteInvoice" invoice-number invoice-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
