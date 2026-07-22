(ns wastetrade.store
  "SSoT for the waste/scrap-wholesale actor, behind a `Store` protocol so
  the backend is a swap, not a rewrite -- the same seam every prior
  `cloud-itonami-isic-*` actor in this fleet uses.

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/wastetrade/store_contract_test.clj), which is the whole point:
  the actor, the Waste Trading Governor and the audit ledger never know
  which SSoT they run on.

  Like the metal-wholesale and textile-wholesale siblings' entities,
  this vertical's `dispatch` and `settle` actuation events apply
  SEQUENTIALLY to the SAME `waste-order` -- a waste/scrap shipment
  dispatch happens first (the shipment leaves the wholesaler's own
  yard/warehouse and crosses the border), invoice settlement happens
  later, on the same order record. This matches the sequential
  dual-actuation shape, with dedicated double-actuation-guard booleans
  (`:dispatched?`/`:invoiced?`, never a `:status` value).

  The `waste-order` record carries THREE evidence surfaces the Waste
  Trading Governor reads independently: the generic per-jurisdiction
  counterparty-diligence facts (`:credit-cleared?` / `:contract-terms` /
  `:sanctions-screened?`, same shape as every sibling), a BILATERAL pair
  of prior-informed-consent facts
  (`:transboundary-notification-filed?` /
  `:destination-country-consent-documented?`) that exist ONLY on this
  vertical, PLUS a dedicated `:destination-country` field distinct from
  `:jurisdiction` -- `:jurisdiction` is the EXPORTING trader's own
  regulatory jurisdiction (the general customs/export-control
  spec-basis key, same field every sibling uses), `:destination-country`
  is the IMPORTING country whose competent authority's consent this
  order's Prior Informed Consent facts are actually about. See
  `wastetrade.governor`'s `prior-informed-consent-missing-violations`
  for why these are a SEPARATE check rather than folded into the generic
  evidence checklist.

  The ledger stays append-only on every backend: 'which waste-order was
  verified for a jurisdiction with no official spec-basis, which
  counterparty had credit-uncleared / no contract / an unverified
  prior-informed-consent / an unresolved sanctions-screening flag, which
  order had waste/scrap dispatched, which invoice was settled, on what
  jurisdictional and bilateral-consent basis, approved by whom' is
  always a query over an immutable log -- the audit trail a regulator,
  a destination-country competent authority, or an operator trusting a
  waste-wholesale actor needs, and the evidence an operator needs if a
  dispatch or an invoice is later disputed."
  (:require [wastetrade.registry :as registry]
            [langchain.db :as d]
            [langchain-store.core :as ls]))

(defprotocol Store
  (waste-order [s id])
  (all-waste-orders [s])
  (assessment-of [s waste-order-id] "committed consent assessment, or nil")
  (ledger [s])
  (dispatch-history [s] "the append-only waste-dispatch history (wastetrade.registry drafts)")
  (invoice-history [s] "the append-only waste-invoice history (wastetrade.registry drafts)")
  (next-dispatch-sequence [s jurisdiction] "next dispatch-number sequence for a jurisdiction")
  (next-invoice-sequence [s jurisdiction] "next invoice-number sequence for a jurisdiction")
  (waste-order-already-dispatched? [s waste-order-id] "has waste/scrap already been dispatched for this order?")
  (waste-order-already-invoiced? [s waste-order-id] "has this order's invoice already been settled?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-waste-orders [s waste-orders] "replace/seed the waste-order directory (map id->waste-order)"))

;; ----------------------------- demo data -----------------------------

(defn- base-order
  "The neutral, clean waste-order shape (every field in its safe state),
  so each demo order below isolates exactly ONE failure mode by
  overriding a single field. `:waste-stream-type` defaults to
  'waste electrical and electronic equipment (WEEE / e-waste)' (a
  HAZARDOUS stream) so the base order also proves the happy path through
  the prior-informed-consent check, not just around it."
  [overrides]
  (merge {:id "wo-1" :order-id "WO-2026-0001"
          :waste-stream-type "waste electrical and electronic equipment (WEEE / e-waste)"
          :counterparty "Kestrel Recycling & Recovery GmbH"
          :price 18500.00 :contract-terms "FOB, net 30 days"
          :credit-cleared? true :sanctions-screened? true
          :transboundary-notification-filed? true
          :destination-country-consent-documented? true
          :dispatched? false :invoiced? false
          :jurisdiction "JPN" :destination-country "DEU" :status :intake
          :dispatch-number nil :invoice-number nil}
         overrides))

(defn demo-data
  "A small, self-contained waste-order set covering both actuation
  lifecycles (dispatch, invoice settlement) plus the Waste Trading
  Governor's own checks, so the actor + tests run offline. Each
  violation order isolates exactly ONE failure mode (the rest stay
  clean) following the 'exercise the failure mode directly, never only
  via a happy-path actuation' discipline every sibling governor's demo
  data establishes.

  `wo-6` and `wo-7` together prove the prior-informed-consent check is
  genuinely HAZARD-TYPE-gated: `wo-6` (WEEE/e-waste destined for the
  USA -- a Basel non-Party, BUT with its own parallel RCRA
  destination-consent requirement -- with NEITHER sub-fact on file)
  HOLDS; `wo-7` (sorted ferrous scrap, the SAME undocumented facts)
  does NOT -- sorted ferrous scrap is not a hazardous stream in this
  actor's scope, and the USA-non-Party detail on `wo-6` additionally
  proves the check is UNCONDITIONAL across jurisdiction, not merely
  'requires Basel Party status'. `wo-8` proves the check folds BOTH
  sub-facts into one rule: notification filed but destination consent
  still undocumented HOLDS just as surely as neither being on file."
  []
  {:waste-orders
   (into {}
         (for [o [(base-order {:id "wo-1" :order-id "WO-2026-0001"})
                  (base-order {:id "wo-2" :order-id "WO-2026-0002"
                               :counterparty "Atlantis Waste Traders Ltd"
                               :jurisdiction "ATL"})
                  (base-order {:id "wo-3" :order-id "WO-2026-0003"
                               :counterparty "Cedar Scrap & Salvage Corp"
                               :credit-cleared? false})
                  (base-order {:id "wo-4" :order-id "WO-2026-0004"
                               :counterparty "Delta Recyclables BV"
                               :contract-terms nil})
                  (base-order {:id "wo-5" :order-id "WO-2026-0005"
                               :counterparty "Eagle Environmental Services SA"
                               :sanctions-screened? false})
                  (base-order {:id "wo-6" :order-id "WO-2026-0006"
                               :waste-stream-type "used lead-acid batteries (ULAB)"
                               :counterparty "Fenwick Battery Recyclers Inc"
                               :destination-country "USA"
                               :transboundary-notification-filed? false
                               :destination-country-consent-documented? false})
                  (base-order {:id "wo-7" :order-id "WO-2026-0007"
                               :waste-stream-type "ferrous scrap metal (sorted, HS 7204)"
                               :counterparty "Granite Ferrous Scrap Traders Inc"
                               :transboundary-notification-filed? false
                               :destination-country-consent-documented? false})
                  (base-order {:id "wo-8" :order-id "WO-2026-0008"
                               :waste-stream-type "hazardous chemical waste (spent solvents)"
                               :counterparty "Halcyon Hazardous Waste Logistics AG"
                               :transboundary-notification-filed? true
                               :destination-country-consent-documented? false})]]
           [(:id o) o]))})

;; ----------------------------- shared commit logic -----------------------------

(defn- dispatch-order!
  "Backend-agnostic `:order/mark-dispatched` -- looks up the waste-order
  via the protocol and drafts the waste-dispatch record, and returns
  {:result .. :waste-order-patch ..} for the caller to persist."
  [s waste-order-id]
  (let [wo (waste-order s waste-order-id)
        seq-n (next-dispatch-sequence s (:jurisdiction wo))
        result (registry/register-dispatch-record waste-order-id (:jurisdiction wo) seq-n)]
    {:result result
     :waste-order-patch {:dispatched? true
                         :dispatch-number (get result "dispatch_number")}}))

(defn- invoice-order!
  "Backend-agnostic `:order/mark-invoiced` -- looks up the waste-order
  via the protocol and drafts the waste-invoice record, and returns
  {:result .. :waste-order-patch ..} for the caller to persist."
  [s waste-order-id]
  (let [wo (waste-order s waste-order-id)
        seq-n (next-invoice-sequence s (:jurisdiction wo))
        result (registry/register-invoice-record waste-order-id (:jurisdiction wo) seq-n)]
    {:result result
     :waste-order-patch {:invoiced? true
                         :invoice-number (get result "invoice_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (waste-order [_ id] (get-in @a [:waste-orders id]))
  (all-waste-orders [_] (sort-by :id (vals (:waste-orders @a))))
  (assessment-of [_ waste-order-id] (get-in @a [:assessments waste-order-id]))
  (ledger [_] (:ledger @a))
  (dispatch-history [_] (:dispatches @a))
  (invoice-history [_] (:invoices @a))
  (next-dispatch-sequence [_ jurisdiction] (get-in @a [:dispatch-sequences jurisdiction] 0))
  (next-invoice-sequence [_ jurisdiction] (get-in @a [:invoice-sequences jurisdiction] 0))
  (waste-order-already-dispatched? [_ waste-order-id] (boolean (get-in @a [:waste-orders waste-order-id :dispatched?])))
  (waste-order-already-invoiced? [_ waste-order-id] (boolean (get-in @a [:waste-orders waste-order-id :invoiced?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :order/upsert
      (swap! a update-in [:waste-orders (:id value)] merge value)

      :consent-assessment/set
      (swap! a assoc-in [:assessments (first path)] payload)

      :order/mark-dispatched
      (let [waste-order-id (first path)
            {:keys [result waste-order-patch]} (dispatch-order! s waste-order-id)
            jurisdiction (:jurisdiction (waste-order s waste-order-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:dispatch-sequences jurisdiction] (fnil inc 0))
                       (update-in [:waste-orders waste-order-id] merge waste-order-patch)
                       (update :dispatches registry/append result))))
        result)

      :order/mark-invoiced
      (let [waste-order-id (first path)
            {:keys [result waste-order-patch]} (invoice-order! s waste-order-id)
            jurisdiction (:jurisdiction (waste-order s waste-order-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:invoice-sequences jurisdiction] (fnil inc 0))
                       (update-in [:waste-orders waste-order-id] merge waste-order-patch)
                       (update :invoices registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-waste-orders [s waste-orders] (when (seq waste-orders) (swap! a assoc :waste-orders waste-orders)) s))

(defn seed-db
  "A MemStore seeded with the demo waste-order set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :assessments {}
                           :ledger [] :dispatch-sequences {} :dispatches []
                           :invoice-sequences {} :invoices []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (assessment payloads, ledger facts, dispatch/
  invoice records) are stored as EDN strings so `langchain.db`
  doesn't expand them into sub-entities -- the same convention every
  sibling actor's store uses."
  {:waste-order/id                       {:db/unique :db.unique/identity}
   :assessment/waste-order-id            {:db/unique :db.unique/identity}
   :ledger/seq                           {:db/unique :db.unique/identity}
   :dispatch/seq                         {:db/unique :db.unique/identity}
   :invoice/seq                          {:db/unique :db.unique/identity}
   :dispatch-sequence/jurisdiction       {:db/unique :db.unique/identity}
   :invoice-sequence/jurisdiction        {:db/unique :db.unique/identity}})

;; the EDN-blob codec (enc/dec*) is shared machinery -- see
;; kotoba-lang/langchain-store's docstring (ADR-2607141600).
(defn- enc [v] (ls/enc v))
(defn- dec* [s] (ls/dec* s))

;; Every waste-order field is stored as its own Datomic attr so a
;; governor pull reads the exact ground truth (no blob decode). Boolean
;; fields are coerced on read so a missing attr reads back as false
;; (parity with MemStore). [field-key tx-attr boolean?]
(def ^:private waste-order-fields
  [[:id :waste-order/id false]
   [:order-id :waste-order/order-id false]
   [:waste-stream-type :waste-order/waste-stream-type false]
   [:counterparty :waste-order/counterparty false]
   [:price :waste-order/price false]
   [:contract-terms :waste-order/contract-terms false]
   [:credit-cleared? :waste-order/credit-cleared? true]
   [:sanctions-screened? :waste-order/sanctions-screened? true]
   [:transboundary-notification-filed? :waste-order/transboundary-notification-filed? true]
   [:destination-country-consent-documented? :waste-order/destination-country-consent-documented? true]
   [:dispatched? :waste-order/dispatched? true]
   [:invoiced? :waste-order/invoiced? true]
   [:jurisdiction :waste-order/jurisdiction false]
   [:destination-country :waste-order/destination-country false]
   [:status :waste-order/status false]
   [:dispatch-number :waste-order/dispatch-number false]
   [:invoice-number :waste-order/invoice-number false]])

(defn- waste-order->tx [wo]
  (reduce (fn [tx [k attr _bool?]]
            (let [v (get wo k)]
              (cond-> tx (some? v) (assoc attr v))))
          {:waste-order/id (:id wo)}
          waste-order-fields))

(def ^:private waste-order-pull (mapv second waste-order-fields))

(defn- pull->waste-order [m]
  (when (:waste-order/id m)
    (reduce (fn [wo [k attr bool?]]
              (let [v (get m attr)]
                (cond
                  bool?        (assoc wo k (boolean v))
                  (some? v)    (assoc wo k v)
                  :else        wo)))
            {:id (:waste-order/id m)}
            waste-order-fields)))

(defrecord DatomicStore [conn]
  Store
  (waste-order [_ id]
    (pull->waste-order (d/pull (d/db conn) waste-order-pull [:waste-order/id id])))
  (all-waste-orders [_]
    (->> (d/q '[:find [?id ...] :where [?e :waste-order/id ?id]] (d/db conn))
         (map #(pull->waste-order (d/pull (d/db conn) waste-order-pull [:waste-order/id %])))
         (sort-by :id)))
  (assessment-of [_ waste-order-id]
    (dec* (d/q '[:find ?p . :in $ ?woid
                :where [?a :assessment/waste-order-id ?woid] [?a :assessment/payload ?p]]
              (d/db conn) waste-order-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (dispatch-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :dispatch/seq ?s] [?e :dispatch/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (invoice-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :invoice/seq ?s] [?e :invoice/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-dispatch-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :dispatch-sequence/jurisdiction ?j] [?e :dispatch-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-invoice-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :invoice-sequence/jurisdiction ?j] [?e :invoice-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (waste-order-already-dispatched? [s waste-order-id]
    (boolean (:dispatched? (waste-order s waste-order-id))))
  (waste-order-already-invoiced? [s waste-order-id]
    (boolean (:invoiced? (waste-order s waste-order-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :order/upsert
      (d/transact! conn [(waste-order->tx value)])

      :consent-assessment/set
      (d/transact! conn [{:assessment/waste-order-id (first path) :assessment/payload (enc payload)}])

      :order/mark-dispatched
      (let [waste-order-id (first path)
            {:keys [result waste-order-patch]} (dispatch-order! s waste-order-id)
            jurisdiction (:jurisdiction (waste-order s waste-order-id))
            next-n (inc (next-dispatch-sequence s jurisdiction))]
        (d/transact! conn
                     [(waste-order->tx (assoc waste-order-patch :id waste-order-id))
                      {:dispatch-sequence/jurisdiction jurisdiction :dispatch-sequence/next next-n}
                      {:dispatch/seq (count (dispatch-history s)) :dispatch/record (enc (get result "record"))}])
        result)

      :order/mark-invoiced
      (let [waste-order-id (first path)
            {:keys [result waste-order-patch]} (invoice-order! s waste-order-id)
            jurisdiction (:jurisdiction (waste-order s waste-order-id))
            next-n (inc (next-invoice-sequence s jurisdiction))]
        (d/transact! conn
                     [(waste-order->tx (assoc waste-order-patch :id waste-order-id))
                      {:invoice-sequence/jurisdiction jurisdiction :invoice-sequence/next next-n}
                      {:invoice/seq (count (invoice-history s)) :invoice/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-waste-orders [s waste-orders]
    (when (seq waste-orders) (d/transact! conn (mapv waste-order->tx (vals waste-orders)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:waste-orders ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [waste-orders]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-waste-orders s waste-orders))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo waste-order set -- the
  Datomic-backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
