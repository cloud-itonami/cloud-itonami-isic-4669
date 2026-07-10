(ns wastetrade.wastetradeadvisor
  "WasteTradeAdvisor client -- the *contained intelligence node* for the
  waste/scrap-wholesale actor.

  It normalizes waste-order intake, drafts a per-jurisdiction
  counterparty-diligence / sanctions evidence checklist (citing the
  general trade spec-basis) PLUS a prior-informed-consent citation when
  the waste-stream type is hazardous, drafts the waste/scrap dispatch
  action, and drafts the invoice-settlement action. CRITICAL: it is a
  smart-but-untrusted advisor. It returns a *proposal* (with a rationale
  + the fields it cited), never a committed record or a real dispatch/
  settlement. Every output is censored downstream by
  `wastetrade.governor` before anything touches the SSoT, and
  `:delivery/dispatch`/`:invoice/settle` proposals NEVER auto-commit at
  any phase -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :delivery/dispatch | :invoice/settle | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [wastetrade.facts :as facts]
            [wastetrade.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the order-id, counterparty, waste-stream-type,
  destination-country or any physical/commercial value. High confidence,
  low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "廃棄物卸売オーダー記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :order/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- verify-consent
  "Per-jurisdiction counterparty-diligence / sanctions evidence
  checklist draft, PLUS -- when `:waste-stream-type` is classified
  hazardous -- a prior-informed-consent citation drawn from
  `wastetrade.facts/consent-citation` for the order's own
  `:destination-country`, PLUS -- when the order is a WEEE/e-waste
  stream -- an INFORMATIONAL note surfacing the private, voluntary
  R2v3/e-Stewards certification schemes (never a legal requirement this
  actor enforces, see `wastetrade.facts/e-waste-certification-schemes`).
  `:no-spec?` injects the failure mode we must defend against: proposing
  a checklist for a jurisdiction with NO official spec-basis in
  `wastetrade.facts` -- the Waste Trading Governor must reject this
  (never invent a jurisdiction's requirements). The prior-informed-
  consent citation is informational only here -- the governor's own
  `prior-informed-consent-missing-violations` check re-verifies the
  order's OWN `:transboundary-notification-filed?`/
  `:destination-country-consent-documented?` facts directly at
  `:delivery/dispatch`, independent of what this advisor cites."
  [db {:keys [subject no-spec?]}]
  (let [wo (store/waste-order db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction wo))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "wastetrade.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :consent-assessment/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      (let [hazardous? (facts/hazardous-waste-stream? (:waste-stream-type wo))
            pic-basis (when hazardous? (facts/consent-citation (:destination-country wo)))
            e-waste? (facts/e-waste-order? (:waste-stream-type wo))]
        {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                          (count (:required-evidence sb)) " 件を提案"
                          (when hazardous? "、事前同意(PIC)確認を含む")
                          (when e-waste? "、R2v3/e-Stewards認証(任意)情報を付記"))
         :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb)
                          (when pic-basis
                            (str " / 事前同意(PIC)根拠: " (:legal-basis pic-basis)
                                 " (" (:owner-authority pic-basis) ")")))
         :cites      (cond-> [(:legal-basis sb) (:provenance sb)]
                       pic-basis (conj (:legal-basis pic-basis)))
         :effect     :consent-assessment/set
         :value      (cond-> {:jurisdiction iso3
                              :checklist (:required-evidence sb)
                              :spec-basis (:provenance sb)
                              :legal-basis (:legal-basis sb)}
                       pic-basis (assoc :consent-basis (:legal-basis pic-basis))
                       e-waste? (assoc :e-waste-certifications (vec (keys facts/e-waste-certification-schemes))))
         :stake      nil
         :confidence 0.9}))))

(defn- propose-dispatch
  "Draft the actual WASTE-DISPATCH action -- dispatching a real waste/
  scrap shipment across a border to a counterparty. ALWAYS `:stake
  :delivery/dispatch` -- this is a REAL-WORLD act (an autonomous
  sorting/baling robot stages and an autonomous loader physically
  performs the outbound loadout at the wholesale yard, or an operator
  does), never a draft the actor may auto-run. See README `Actuation`:
  no phase ever adds this op to a phase's `:auto` set
  (`wastetrade.phase`); the governor also always escalates on
  `:delivery/dispatch`. Two independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [wo (store/waste-order db subject)
        credit-ok? (and wo (true? (:credit-cleared? wo)))
        contract-ok? (and wo (some? (:contract-terms wo))
                          (not= "" (:contract-terms wo)))
        hazardous? (and wo (facts/hazardous-waste-stream? (:waste-stream-type wo)))
        pic-ok? (or (not hazardous?)
                    (and (true? (:transboundary-notification-filed? wo))
                         (true? (:destination-country-consent-documented? wo))))
        sanctions-ok? (and wo (true? (:sanctions-screened? wo)))]
    {:summary    (str subject " 向け出荷提案"
                      (when wo (str " (counterparty=" (:counterparty wo)
                                    ", waste-stream=" (:waste-stream-type wo)
                                    ", destination=" (:destination-country wo) ")")))
     :rationale  (if wo
                   (str "credit-cleared?=" credit-ok?
                        " contract-on-file?=" contract-ok?
                        " hazardous-waste-stream?=" hazardous?
                        " prior-informed-consent-verified?=" pic-ok?
                        " sanctions-screened?=" sanctions-ok?)
                   "waste-orderが見つかりません")
     :cites      (if wo [subject] [])
     :effect     :order/mark-dispatched
     :value      {:waste-order-id subject}
     :stake      :delivery/dispatch
     :confidence (if (and credit-ok? contract-ok? pic-ok? sanctions-ok?) 0.9 0.3)}))

(defn- propose-invoice
  "Draft the actual INVOICE-SETTLEMENT action -- settling a real
  waste-trade invoice (the money side of a waste/scrap-wholesale trade,
  custody/financial transfer). ALWAYS `:stake :invoice/settle` -- this
  is a REAL-WORLD act (real money moves between counterparty and
  trader), never a draft the actor may auto-run. See README
  `Actuation`: no phase ever adds this op to a phase's `:auto` set
  (`wastetrade.phase`); the governor also always escalates on
  `:invoice/settle`. Two independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [wo (store/waste-order db subject)
        dispatched? (and wo (:dispatched? wo))
        sanctions-ok? (and wo (true? (:sanctions-screened? wo)))]
    {:summary    (str subject " 向け請求提案"
                      (when wo (str " (counterparty=" (:counterparty wo) ")")))
     :rationale  (if wo
                   (str "dispatched?=" dispatched?
                        " sanctions-screened?=" sanctions-ok?)
                   "waste-orderが見つかりません")
     :cites      (if wo [subject] [])
     :effect     :order/mark-invoiced
     :value      {:waste-order-id subject}
     :stake      :invoice/settle
     :confidence (if (and dispatched? sanctions-ok?) 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :order/intake       (normalize-intake db request)
    :consent/verify     (verify-consent db request)
    :delivery/dispatch  (propose-dispatch db request)
    :invoice/settle     (propose-invoice db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは廃棄物・スクラップ卸売事業者の出荷・請求エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。"
       "説明や前置きは一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:order/upsert|:consent-assessment/set|:order/mark-dispatched|"
       ":order/mark-invoiced) "
       ":stake(:delivery/dispatch か :invoice/settle か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の関税・輸出管理要件を絶対に創作してはいけません。"
       "有害廃棄物該当の荷口について、国境を越える移動事前通報や仕向国の権限ある当局の"
       "事前同意(Prior Informed Consent)の状態を偽って報告してはいけません。"
       "R2v3/e-Stewards等の任意認証は法的要件ではないため、法域要件と混同してはいけません。"
       "取引先信用審査・契約有無・制裁スクリーニングの状態も偽って報告してはいけません。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :consent/verify    {:waste-order (store/waste-order st subject)}
    :delivery/dispatch {:waste-order (store/waste-order st subject)}
    :invoice/settle    {:waste-order (store/waste-order st subject)}
    {:waste-order (store/waste-order st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Waste Trading Governor
  escalates/holds -- an LLM hiccup can never auto-dispatch waste/scrap
  or auto-settle an invoice."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :wastetradeadvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
