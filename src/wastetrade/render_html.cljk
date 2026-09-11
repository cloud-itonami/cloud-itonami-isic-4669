(ns wastetrade.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 for `cloud-itonami-isic-4669`: the
  console committed in this repo's initial commit was HAND-WRITTEN
  (raw hex colours, no generator, no link to the actor at all). This
  namespace replaces it with a page that is DRIVEN BY THE REAL STACK --
  `wastetrade.operation` (a langgraph-clj StateGraph) ->
  `wastetrade.governor` -> `wastetrade.store` -- and rendered from what
  that run actually produced. Every waste-order id, counterparty,
  jurisdiction, hold rule, hold detail, confidence, dispatch number and
  invoice number on the page is read back out of the store / the graph
  run state. Nothing is typed in by hand.

  The scenario below is an EXTENSION of this repo's own demo driver
  (`wastetrade.sim`, `clojure -M:dev:run`, run and read BEFORE this file
  was written so the seeded ids `wo-1`..`wo-8` and their real outputs
  were known). It adds three probes `sim` does not perform:

    * a PHASE-0 `:order/intake` -- the rollout gate holding a write that
      the governor itself was happy with (`:phase-disabled`),
    * a `:delivery/dispatch` on `wo-1` BEFORE its consent assessment is
      on file -- the only way to reach `:evidence-incomplete` in
      isolation, and
    * a REJECTED approval on `wo-7`'s invoice -- the human-in-the-loop
      branch (`:approval-rejected`) that nothing else exercises.

  Together with `sim`'s own failure modes that means ALL EIGHT of
  `wastetrade.governor`'s rules fire in this one run, each isolated on
  its own waste-order. `-main` enforces that as a BUILD-TIME INVARIANT:
  it throws unless every rule in `governor-rules` below was actually
  observed in the emitted ledger, so this page can never silently
  degrade into a page with no holds on it.

  Determinism: no timestamp, no random, no wall-clock anywhere in the
  page content -- two consecutive runs are byte-identical.

  CSS: the DADS (デジタル庁デザインシステム) primitives inlined in
  `dds-css` are copied from THIS REPO'S OWN vendored product face
  (`docs/index.html`, which carries the full DADS token block), so the
  console matches the product it belongs to and the build stays offline.
  The `jp-go-dds` git dependency is deliberately NOT added: it would
  make this build network-dependent, and its `tokens/bridge-css` bridges
  the `--hig-*` contract rather than the `--color-*` primitives this
  console (and `docs/index.html`) actually reference.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [kotoba.lang.text :as str]
            [langgraph.graph :as g]
            [wastetrade.facts :as facts]
            [wastetrade.operation :as op]
            [wastetrade.phase :as phase]
            [wastetrade.store :as store]))

;; ----------------------------- scenario -----------------------------

(def ^:private supervisor
  "The human operator identity every run below is executed as. Phase 3
  is `wastetrade.phase/default-phase` (supervised-auto)."
  {:actor-id "op-1" :actor-role :trading-supervisor :phase 3})

(def ^:private read-only-supervisor
  "The SAME operator at phase 0 (read-only) -- used once, to show the
  rollout gate holding a write the governor itself cleared."
  (assoc supervisor :phase 0))

(defn- run-demo!
  "Drives a freshly seeded `wastetrade.store/seed-db` through every
  disposition this actor can reach, one waste-order per failure mode
  (the `wastetrade.sim` discipline: exercise the failure directly, never
  only via a happy path).

  Returns `{:db db :threads [{:tid .. :op .. :subject .. :audit ..} ..]}`
  where `:audit` is the graph's own `:audit` channel for that thread --
  real run output, which is where the approver identity lives (see
  `attribution-rows`: it does NOT survive into the store for the two
  actuation effects)."
  []
  (let [db (store/seed-db)
        actor (op/build db)
        threads (atom [])
        exec! (fn [tid request ctx]
                (let [r (g/run* actor {:request request :context ctx}
                                {:thread-id tid})]
                  (swap! threads conj {:tid tid
                                       :op (:op request)
                                       :subject (:subject request)
                                       :phase (:phase ctx)
                                       :audit (get-in r [:state :audit])
                                       :disposition (get-in r [:state :disposition])})
                  r))
        resume! (fn [tid status]
                  (let [r (g/run* actor {:approval {:status status :by "op-1"}}
                                  {:thread-id tid :resume? true})]
                    (swap! threads
                           (fn [ts]
                             (mapv #(if (= tid (:tid %))
                                      (assoc %
                                             :audit (get-in r [:state :audit])
                                             :disposition (get-in r [:state :disposition]))
                                      %)
                                   ts)))
                    r))
        approve! #(resume! % :approved)
        reject!  #(resume! % :rejected)]

    ;; 0. rollout gate: phase 0 is read-only, so even a clean intake holds.
    (exec! "t0-phase0-intake" {:op :order/intake :subject "wo-1"
                               :patch {:id "wo-1"}}
           read-only-supervisor)

    ;; 1. dispatch BEFORE the jurisdiction evidence checklist is on file.
    (exec! "t1-dispatch-early" {:op :delivery/dispatch :subject "wo-1"} supervisor)

    ;; 2. wo-1 full clean lifecycle: intake -> verify -> dispatch -> settle.
    (exec! "t1-intake" {:op :order/intake :subject "wo-1"
                        :patch {:id "wo-1"
                                :counterparty "Kestrel Recycling & Recovery GmbH"}}
           supervisor)
    (exec! "t1-verify" {:op :consent/verify :subject "wo-1"} supervisor)
    (approve! "t1-verify")
    (exec! "t1-dispatch" {:op :delivery/dispatch :subject "wo-1"} supervisor)
    (approve! "t1-dispatch")
    (exec! "t1-settle" {:op :invoice/settle :subject "wo-1"} supervisor)
    (approve! "t1-settle")

    ;; 3. double-actuation guards, off dedicated booleans (never :status).
    (exec! "t1-dispatch-again" {:op :delivery/dispatch :subject "wo-1"} supervisor)
    (exec! "t1-settle-again" {:op :invoice/settle :subject "wo-1"} supervisor)

    ;; 4. wo-2 -- jurisdiction "ATL" has no entry in wastetrade.facts.
    (exec! "t2-verify" {:op :consent/verify :subject "wo-2"} supervisor)

    ;; 5..8 -- one isolated HARD hold per counterparty-diligence fact.
    (doseq [[wo tid-v tid-d] [["wo-3" "t3-verify" "t3-dispatch"]
                              ["wo-4" "t4-verify" "t4-dispatch"]
                              ["wo-5" "t5-verify" "t5-dispatch"]
                              ["wo-6" "t6-verify" "t6-dispatch"]]]
      (exec! tid-v {:op :consent/verify :subject wo} supervisor)
      (approve! tid-v)
      (exec! tid-d {:op :delivery/dispatch :subject wo} supervisor))

    ;; 9. wo-7 -- green-list control: SAME undocumented PIC facts as wo-6,
    ;;    non-hazardous stream, so it dispatches cleanly.
    (exec! "t7-verify" {:op :consent/verify :subject "wo-7"} supervisor)
    (approve! "t7-verify")
    (exec! "t7-dispatch" {:op :delivery/dispatch :subject "wo-7"} supervisor)
    (approve! "t7-dispatch")

    ;; 10. wo-8 -- notification FILED but destination consent undocumented.
    (exec! "t8-verify" {:op :consent/verify :subject "wo-8"} supervisor)
    (approve! "t8-verify")
    (exec! "t8-dispatch" {:op :delivery/dispatch :subject "wo-8"} supervisor)

    ;; 11. human-in-the-loop REJECTION -- the supervisor declines wo-7's
    ;;     invoice settlement even though the governor cleared it.
    (exec! "t7-settle" {:op :invoice/settle :subject "wo-7"} supervisor)
    (reject! "t7-settle")

    {:db db :threads @threads}))

;; ----------------------------- governor rule contract -----------------------------

(def ^:private governor-rules
  "`wastetrade.governor`'s published rule contract: the six numbered
  HARD checks plus the two double-actuation guards, in the order that
  namespace's docstring lists them. The `exercised?` column of the
  rendered table is DERIVED from the run, and `-main` throws if any row
  here was not actually observed -- so this list is verified by the
  build, not merely asserted by a comment."
  [[:no-spec-basis
    ":consent/verify · :delivery/dispatch · :invoice/settle"
    "公式 spec-basis (wastetrade.facts/catalog) の引用が無い提案。法域要件を創作させない。"]
   [:evidence-incomplete
    ":delivery/dispatch · :invoice/settle"
    "法域の必要書類 3 件 (信用審査記録 / 契約書または PO / 制裁スクリーニング記録) が充足していない。"]
   [:credit-uncleared
    ":delivery/dispatch"
    "取引先の信用審査 (:credit-cleared?) が未了のまま出荷しようとした。"]
   [:contract-missing
    ":delivery/dispatch"
    "契約条項 (:contract-terms) の記録が無いまま出荷しようとした。"]
   [:prior-informed-consent-missing
    ":delivery/dispatch"
    "有害廃棄物該当の荷口で、越境移動事前通報 (:transboundary-notification-filed?) と仕向国当局の事前同意 (:destination-country-consent-documented?) の双方が揃っていない。この分野固有の二国間チェック。"]
   [:counterparty-sanctions-flag-unresolved
    ":delivery/dispatch · :invoice/settle"
    "取引先の制裁スクリーニング (OFAC 等, :sanctions-screened?) が未了。廃棄物も資金も動かさない。"]
   [:already-dispatched
    ":delivery/dispatch"
    "同一 waste-order の二重出荷。専用の :dispatched? を見る (:status は見ない)。"]
   [:already-invoiced
    ":invoice/settle"
    "同一 waste-order の二重請求。専用の :invoiced? を見る (:status は見ない)。"]])

;; ----------------------------- derivations -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- governor-holds
  "Every `:governor-hold` fact the run wrote to the append-only ledger."
  [ledger]
  (filterv #(= :governor-hold (:t %)) ledger))

(defn- hard-holds
  "Governor holds carrying at least one real rule violation. A hold with
  an EMPTY `:basis` is the rollout-phase gate refusing the write, not a
  governor rule -- kept separate so the two are never conflated."
  [ledger]
  (filterv #(seq (:basis %)) (governor-holds ledger)))

(defn- phase-holds [ledger]
  (filterv #(empty? (:basis %)) (governor-holds ledger)))

(defn- rejection-holds [ledger]
  (filterv #(= :approval-rejected (:t %)) ledger))

(defn- observed-rules [ledger]
  (into #{} (mapcat :basis) (governor-holds ledger)))

(defn- last-fact-for [ledger id]
  (last (filter #(= id (:subject %)) ledger)))

(defn- approver-key?
  "Does `artifact` (a map with keyword OR string keys, depending on which
  side of the store wrote it) actually carry an approver attribution?
  DERIVED by scanning the keys rather than hardcoded, so if the store is
  later fixed to persist `:approved-by` on the actuation registers this
  page corrects itself on the next build."
  [artifact]
  (boolean (and (map? artifact)
                (some #(re-find #"(?i)approv" (name %)) (keys artifact)))))

(defn- artifact-for
  "The store artifact an effect actually produced, so `approver-key?`
  can be asked of the REAL committed record."
  [db effect subject]
  (case effect
    :order/upsert            (store/waste-order db subject)
    :consent-assessment/set  (store/assessment-of db subject)
    :order/mark-dispatched   (first (filter #(= subject (get % "waste_order_id"))
                                            (store/dispatch-history db)))
    :order/mark-invoiced     (first (filter #(= subject (get % "waste_order_id"))
                                            (store/invoice-history db)))
    nil))

(def ^:private op->effect
  {:order/intake       :order/upsert
   :consent/verify     :consent-assessment/set
   :delivery/dispatch  :order/mark-dispatched
   :invoice/settle     :order/mark-invoiced})

(defn- audit-of [thread t]
  (first (filter #(= t (:t %)) (:audit thread))))

(defn- attribution-rows
  "One row per thread that reached the human-approval node. Joins the
  approver identity -- which lives ONLY in the graph's `:audit` channel
  (`:approval-granted`, `:by`) -- against the artifact the store
  actually kept, and reports honestly which side holds it."
  [db threads]
  (for [th threads
        :let [granted (audit-of th :approval-granted)
              rejected (audit-of th :approval-rejected)
              requested (audit-of th :approval-requested)]
        :when (or granted rejected requested)]
    (let [effect (op->effect (:op th))
          artifact (when granted (artifact-for db effect (:subject th)))
          in-store? (approver-key? artifact)]
      {:tid (:tid th)
       :op (:op th)
       :subject (:subject th)
       :reason (:reason requested)
       :outcome (cond granted :approved rejected :rejected :else :pending)
       ;; `:approval-granted` carries `:by`; the rejection branch merges
       ;; over `governor/hold-fact`, which names the operator `:actor`.
       :approver (or (:by granted) (:by rejected) (:actor rejected))
       :effect effect
       :artifact-kind (cond (nil? artifact) "—"
                            (= effect :consent-assessment/set) "assessment payload"
                            (= effect :order/mark-dispatched) "waste-dispatch register"
                            (= effect :order/mark-invoiced) "waste-invoice register"
                            :else "waste-order record")
       :approver-in-store? in-store?})))

;; ----------------------------- CSS (DADS primitives, vendored) -----------------------------

(def ^:private dds-css
  "Only the DADS primitives this console actually references, copied
  from `docs/index.html`'s vendored token block so the two faces agree.
  NOTE the measured shape of the semantic error pair: `-error-1` is
  red-800 and `-error-2` is red-900 -- BOTH dark. They are not a
  strong/weak pair, so tinted backgrounds below use the `-50` primitive
  steps, never `-error-2`."
  (str/join
   "\n"
   [":root {"
    "  --color-neutral-white: #ffffff;"
    "  --color-neutral-solid-gray-50: #f2f2f2;"
    "  --color-neutral-solid-gray-100: #e6e6e6;"
    "  --color-neutral-solid-gray-200: #cccccc;"
    "  --color-neutral-solid-gray-300: #b3b3b3;"
    "  --color-neutral-solid-gray-536: #767676;"
    "  --color-neutral-solid-gray-600: #666666;"
    "  --color-neutral-solid-gray-700: #4d4d4d;"
    "  --color-neutral-solid-gray-800: #333333;"
    "  --color-neutral-solid-gray-900: #1a1a1a;"
    "  --color-primitive-blue-50: #e8f1fe;"
    "  --color-primitive-blue-100: #d9e6ff;"
    "  --color-primitive-blue-900: #0017c1;"
    "  --color-primitive-blue-1000: #00118f;"
    "  --color-primitive-red-50: #fdeeee;"
    "  --color-primitive-red-100: #ffdada;"
    "  --color-primitive-red-800: #ec0000;"
    "  --color-primitive-red-900: #ce0000;"
    "  --color-primitive-green-50: #e6f5ec;"
    "  --color-primitive-green-100: #c2e5d1;"
    "  --color-primitive-green-800: #197a4b;"
    "  --color-primitive-green-900: #115a36;"
    "  --color-primitive-orange-50: #ffeee2;"
    "  --color-primitive-orange-100: #ffdfca;"
    "  --color-primitive-orange-900: #ac3e00;"
    "  --color-primitive-yellow-50: #fbf5e0;"
    "  --color-primitive-yellow-900: #927200;"
    "  --color-semantic-error-1: var(--color-primitive-red-800);"
    "  --color-semantic-error-2: var(--color-primitive-red-900);"
    "  --color-semantic-success-2: var(--color-primitive-green-800);"
    "  --color-semantic-warning-yellow-2: var(--color-primitive-yellow-900);"
    "  --font-family-sans: \"Noto Sans JP\", -apple-system, BlinkMacSystemFont, sans-serif;"
    "  --font-family-mono: \"Noto Sans Mono\", monospace;"
    "  --elevation-1: 0 2px 8px 1px rgba(0, 0, 0, 0.1), 0 1px 5px 0 rgba(0, 0, 0, 0.3);"
    "}"
    "* { box-sizing: border-box; }"
    "body { margin: 0; background: var(--color-neutral-solid-gray-50);"
    "  color: var(--color-neutral-solid-gray-800); font-family: var(--font-family-sans);"
    "  line-height: 1.7; }"
    "header.bar { display: flex; flex-wrap: wrap; align-items: baseline; gap: .75rem;"
    "  padding: 1.25rem 1.5rem; background: var(--color-neutral-white);"
    "  border-bottom: 4px solid var(--color-primitive-blue-900); }"
    "header.bar h1 { font-size: 1.25rem; margin: 0; font-weight: 700;"
    "  color: var(--color-neutral-solid-gray-900); }"
    "header.bar .badge { margin-left: auto; font-size: .8125rem;"
    "  color: var(--color-neutral-solid-gray-600); }"
    "main { max-width: 76rem; margin: 1.5rem auto; padding: 0 1.5rem 3rem; }"
    ".card { background: var(--color-neutral-white);"
    "  border: 1px solid var(--color-neutral-solid-gray-200); border-radius: 12px;"
    "  padding: 1.5rem; margin-bottom: 1.5rem; box-shadow: var(--elevation-1); }"
    ".card h2 { margin: 0 0 .25rem; font-size: 1.0625rem; font-weight: 700;"
    "  color: var(--color-neutral-solid-gray-900); }"
    ".card p.lede { margin: 0 0 1rem; font-size: .875rem;"
    "  color: var(--color-neutral-solid-gray-600); }"
    ".scroll { overflow-x: auto; }"
    "table { width: 100%; border-collapse: collapse; font-size: .8125rem; }"
    "th, td { text-align: left; padding: .5rem .625rem; vertical-align: top;"
    "  border-bottom: 1px solid var(--color-neutral-solid-gray-100); }"
    "th { font-weight: 700; font-size: .75rem; letter-spacing: .04em;"
    "  text-transform: uppercase; color: var(--color-neutral-solid-gray-700);"
    "  border-bottom: 2px solid var(--color-neutral-solid-gray-300);"
    "  white-space: nowrap; }"
    "td.num { font-variant-numeric: tabular-nums; text-align: right; }"
    "code { font-family: var(--font-family-mono); font-size: .9em;"
    "  background: var(--color-neutral-solid-gray-50);"
    "  border: 1px solid var(--color-neutral-solid-gray-200);"
    "  border-radius: 4px; padding: 1px 5px; }"
    ".ok { color: var(--color-semantic-success-2); font-weight: 700; }"
    ".muted { color: var(--color-neutral-solid-gray-536); }"
    ".pill { display: inline-block; font-size: .75rem; font-weight: 700;"
    "  border-radius: 999px; padding: .0625rem .5rem; white-space: nowrap; }"
    ".pill.hard { background: var(--color-primitive-red-100);"
    "  color: var(--color-semantic-error-2); }"
    ".pill.hazard { background: var(--color-primitive-orange-100);"
    "  color: var(--color-primitive-orange-900); }"
    ".pill.green { background: var(--color-primitive-green-100);"
    "  color: var(--color-primitive-green-900); }"
    ".pill.warn { background: var(--color-primitive-yellow-50);"
    "  color: var(--color-semantic-warning-yellow-2); }"
    ".pill.info { background: var(--color-primitive-blue-100);"
    "  color: var(--color-primitive-blue-1000); }"
    ".pill.neutral { background: var(--color-neutral-solid-gray-100);"
    "  color: var(--color-neutral-solid-gray-700); }"
    "tr.hard-row { background: var(--color-primitive-red-50); }"
    "tr.commit-row { background: var(--color-primitive-green-50); }"
    "tr.phase-row { background: var(--color-primitive-orange-50); }"
    "tr.escalate-row { background: var(--color-primitive-blue-50); }"
    ".note { margin: 1rem 0 0; padding: .75rem 1rem; font-size: .8125rem;"
    "  border-left: 4px solid var(--color-primitive-blue-900);"
    "  background: var(--color-primitive-blue-50);"
    "  color: var(--color-neutral-solid-gray-800); }"
    ".note.defect { border-left-color: var(--color-semantic-error-1);"
    "  background: var(--color-primitive-red-50); }"
    "footer { max-width: 76rem; margin: 0 auto; padding: 0 1.5rem 3rem;"
    "  font-size: .8125rem; color: var(--color-neutral-solid-gray-600); }"]))

;; ----------------------------- row builders -----------------------------

(defn- yn [v]
  (if (true? v)
    "<span class=\"ok\">はい</span>"
    "<span class=\"pill hard\">いいえ</span>"))

(defn- stream-pill [waste-stream-type]
  (cond
    (facts/hazardous-waste-stream? waste-stream-type)
    "<span class=\"pill hazard\">有害 / hazardous</span>"
    (contains? facts/green-list-waste-streams waste-stream-type)
    "<span class=\"pill green\">green list</span>"
    :else "<span class=\"pill neutral\">未分類</span>"))

(defn- status-cell [ledger id]
  (let [f (last-fact-for ledger id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :approval-rejected (:t f)) "<span class=\"pill warn\">approver rejected</span>"
      (= :governor-hold (:t f))
      (if (seq (:basis f))
        (str "<span class=\"pill hard\">HARD hold · "
             (esc (str/join ", " (map name (:basis f)))) "</span>")
        "<span class=\"pill warn\">phase gate hold</span>")
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- order-row [ledger {:keys [id order-id counterparty jurisdiction waste-stream-type
                                 credit-cleared? contract-terms sanctions-screened?
                                 dispatched? invoiced? dispatch-number invoice-number]}]
  (format (str "        <tr><td><code>%s</code></td><td><code>%s</code></td><td>%s</td>"
               "<td>%s %s</td><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td>"
               "<td>%s</td><td>%s</td></tr>")
          (esc id) (esc order-id) (esc counterparty)
          (esc waste-stream-type) (stream-pill waste-stream-type)
          (esc jurisdiction)
          (yn credit-cleared?)
          (if (str/blank? (str contract-terms))
            "<span class=\"pill hard\">未記録</span>"
            (esc contract-terms))
          (yn sanctions-screened?)
          (str (if dispatched?
                 (str "<code>" (esc dispatch-number) "</code>")
                 "<span class=\"muted\">未出荷</span>")
               " / "
               (if invoiced?
                 (str "<code>" (esc invoice-number) "</code>")
                 "<span class=\"muted\">未請求</span>"))
          (status-cell ledger id)))

(defn- pic-row [ledger {:keys [id waste-stream-type destination-country
                               transboundary-notification-filed?
                               destination-country-consent-documented?]}]
  (let [hazardous? (facts/hazardous-waste-stream? waste-stream-type)
        cite (facts/consent-citation destination-country)
        held? (let [f (last-fact-for ledger id)]
                (boolean (some #{:prior-informed-consent-missing} (:basis f))))]
    (format (str "        <tr><td><code>%s</code></td><td>%s</td><td><code>%s</code></td>"
                 "<td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>")
            (esc id)
            (stream-pill waste-stream-type)
            (esc destination-country)
            (if (get facts/basel-party? destination-country)
              "<span class=\"pill info\">Basel 締約国</span>"
              "<span class=\"pill warn\">Basel 非締約国</span>")
            (if cite
              (str (esc (:owner-authority cite)))
              "<span class=\"pill hard\">根拠なし</span>")
            (if hazardous? (yn transboundary-notification-filed?)
                "<span class=\"muted\">n/a (green list)</span>")
            (if hazardous? (yn destination-country-consent-documented?)
                "<span class=\"muted\">n/a (green list)</span>")
            (cond
              held? "<span class=\"pill hard\">HARD hold</span>"
              hazardous? "<span class=\"ok\">PIC 充足</span>"
              :else "<span class=\"pill green\">PIC 検査対象外 (no-op)</span>"))))

(defn- rule-row [ledger [rule ops description]]
  (let [hits (filterv #(some #{rule} (:basis %)) (governor-holds ledger))]
    (format (str "        <tr%s><td><code>:%s</code></td><td><code>%s</code></td><td>%s</td>"
                 "<td>%s</td><td class=\"num\">%s</td></tr>")
            (if (seq hits) " class=\"hard-row\"" "")
            (esc (name rule)) (esc ops) (esc description)
            (if (seq hits)
              "<span class=\"pill hard\">この実行で発火</span>"
              "<span class=\"muted\">未発火</span>")
            (count hits))))

(defn- hold-row [i {:keys [op subject basis violations confidence]}]
  (format (str "        <tr class=\"hard-row\"><td class=\"num\">%s</td><td><code>%s</code></td>"
               "<td><code>%s</code></td><td>%s</td><td>%s</td><td class=\"num\">%s</td></tr>")
          (inc i) (esc (name op)) (esc subject)
          (str/join " " (map #(str "<span class=\"pill hard\">" (esc (name %)) "</span>") basis))
          (esc (str/join " / " (map :detail violations)))
          (esc confidence)))

(defn- phase-row [current-phase [n {:keys [label writes auto]}]]
  (format "        <tr%s><td class=\"num\">%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (if (= n current-phase) " class=\"escalate-row\"" "")
          n (esc label)
          (if (seq writes)
            (str/join " " (map #(str "<code>" (esc %) "</code>") (sort (map str writes))))
            "<span class=\"muted\">なし</span>")
          (if (seq auto)
            (str/join " " (map #(str "<code>" (esc %) "</code>") (sort (map str auto))))
            "<span class=\"muted\">なし</span>")))

(defn- jurisdiction-row [seeded iso3]
  (let [sb (facts/spec-basis iso3)]
    (format (str "        <tr%s><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td>"
                 "<td>%s</td></tr>")
            (if sb "" " class=\"hard-row\"")
            (esc iso3)
            (if (contains? seeded iso3)
              "<span class=\"pill info\">この実行で使用</span>"
              "<span class=\"muted\">未使用</span>")
            (if sb
              (esc (:owner-authority sb))
              "<span class=\"pill hard\">spec-basis 無し → :no-spec-basis で HARD hold</span>")
            (if sb (esc (:legal-basis sb)) "—")
            (if sb (str "<code>" (esc (:provenance sb)) "</code>") "—"))))

(defn- attribution-row [{:keys [tid op subject reason outcome approver artifact-kind
                                approver-in-store?]}]
  (format (str "        <tr%s><td><code>%s</code></td><td><code>%s</code></td>"
               "<td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>")
          (case outcome
            :approved " class=\"commit-row\""
            :rejected " class=\"phase-row\""
            "")
          (esc tid) (esc (name op)) (esc subject)
          (if reason (str "<code>" (esc (name reason)) "</code>") "<span class=\"muted\">—</span>")
          (case outcome
            :approved "<span class=\"ok\">approved</span>"
            :rejected "<span class=\"pill warn\">rejected → HOLD</span>"
            "<span class=\"muted\">pending</span>")
          (if approver (str "<code>" (esc approver) "</code>") "<span class=\"muted\">—</span>")
          (cond
            (= :rejected outcome)
            "<span class=\"muted\">no commit record written</span>"
            approver-in-store?
            (str "<span class=\"ok\">" (esc artifact-kind) " に保持</span>")
            :else
            (str "<span class=\"pill warn\">" (esc artifact-kind)
                 " に無し</span> <span class=\"muted\">(audit only — not in commit record)</span>"))))

(defn- cite-cell
  "`:basis` on a `:committed` fact is the advisor's CITATION list, not a
  rule list -- the legal-basis strings are long, so show how many there
  are plus a clipped first one, with the full text kept verbatim in the
  `title` attribute (clipped for layout, never dropped)."
  [basis]
  (let [cites (mapv str basis)
        joined (str/join " | " cites)
        head (first cites)
        clipped (if (> (count head) 72) (str (subs head 0 72) "…") head)]
    (str "<span class=\"muted\" title=\"" (esc joined) "\">"
         (count cites) " 件の引用 · " (esc clipped) "</span>")))

(defn- ledger-row [{:keys [t op subject disposition basis phase-reason]}]
  (format (str "        <tr class=\"%s\"><td>%s</td><td><code>%s</code></td><td><code>%s</code></td>"
               "<td>%s</td><td>%s</td></tr>")
          (case t
            :committed "commit-row"
            :governor-hold (if (seq basis) "hard-row" "phase-row")
            :approval-rejected "phase-row"
            "")
          (esc (name t))
          (esc (name (or op :n-a)))
          (esc subject)
          (esc (name (or disposition :n-a)))
          (cond
            (= :committed t) (if (seq basis) (cite-cell basis) "<span class=\"muted\">—</span>")
            (seq basis) (str/join " " (map #(str "<span class=\"pill hard\">"
                                                 (esc (name %)) "</span>") basis))
            phase-reason (str "<span class=\"pill warn\">" (esc (name phase-reason)) "</span>")
            :else "<span class=\"muted\">—</span>")))

(defn- register-row [kind r]
  (format (str "        <tr><td>%s</td><td><code>%s</code></td><td><code>%s</code></td>"
               "<td><code>%s</code></td><td>%s</td></tr>")
          (esc kind)
          (esc (get r "record_id"))
          (esc (get r "waste_order_id"))
          (esc (get r "jurisdiction"))
          (if (get r "immutable")
            "<span class=\"ok\">immutable draft</span>"
            "<span class=\"muted\">—</span>")))

;; ----------------------------- page -----------------------------

(defn- section [title lede headers rows]
  (str "  <section class=\"card\">\n"
       "    <h2>" title "</h2>\n"
       "    <p class=\"lede\">" lede "</p>\n"
       "    <div class=\"scroll\">\n"
       "    <table>\n"
       "      <thead><tr>"
       (str/join "" (map #(str "<th>" % "</th>") headers))
       "</tr></thead>\n"
       "      <tbody>\n"
       (str/join "\n" rows) "\n"
       "      </tbody>\n"
       "    </table>\n"
       "    </div>\n"
       "  </section>\n"))

(defn render
  "Renders the whole document from `{:db :threads}` produced by
  `run-demo!`. Reads the store and the graph audit only -- there is no
  literal domain value in this function."
  [{:keys [db threads]}]
  (let [ledger (vec (store/ledger db))
        orders (vec (store/all-waste-orders db))
        hard (hard-holds ledger)
        phase-h (phase-holds ledger)
        rejected (rejection-holds ledger)
        seeded-j (into #{} (map :jurisdiction) orders)
        all-j (vec (sort (into seeded-j (keys facts/catalog))))
        cov (facts/coverage all-j)
        attribution (vec (attribution-rows db threads))
        lost (filterv #(and (= :approved (:outcome %)) (not (:approver-in-store? %)))
                      attribution)]
    (str
     "<!doctype html>\n<html lang=\"ja\"><head><meta charset=\"utf-8\">"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
     "<title>cloud-itonami-isic-4669 · wastetrade — Operator Console</title>"
     "<style>\n" dds-css "\n</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>廃棄物・スクラップ卸売 (ISIC 4669) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · 出荷 / 請求は常に人間承認</span>\n"
     "</header>\n"
     "<main>\n"

     "  <section class=\"card\">\n"
     "    <h2>この実行の要約</h2>\n"
     "    <p class=\"lede\">このページは <code>wastetrade.render-html</code> が "
     "<code>wastetrade.operation</code> (langgraph StateGraph) → "
     "<code>wastetrade.governor</code> → <code>wastetrade.store</code> を実際に走らせ、"
     "その結果だけから生成したものです。手書きの値はありません。</p>\n"
     "    <div class=\"scroll\">\n    <table>\n"
     "      <thead><tr><th>指標</th><th>値</th></tr></thead>\n      <tbody>\n"
     (str/join
      "\n"
      [(format "        <tr><td>waste-order (seed)</td><td class=\"num\">%s</td></tr>" (count orders))
       (format "        <tr><td>監査台帳 fact (append-only)</td><td class=\"num\">%s</td></tr>" (count ledger))
       (format "        <tr><td>HARD governor hold</td><td class=\"num\">%s</td></tr>" (count hard))
       (format "        <tr><td>発火した governor rule (種類)</td><td class=\"num\">%s / %s</td></tr>"
               (count (into #{} (mapcat :basis) hard)) (count governor-rules))
       (format "        <tr><td>rollout phase gate による hold</td><td class=\"num\">%s</td></tr>" (count phase-h))
       (format "        <tr><td>人間が却下した提案</td><td class=\"num\">%s</td></tr>" (count rejected))
       (format "        <tr><td>出荷レジスタ (draft)</td><td class=\"num\">%s</td></tr>"
               (count (store/dispatch-history db)))
       (format "        <tr><td>請求レジスタ (draft)</td><td class=\"num\">%s</td></tr>"
               (count (store/invoice-history db)))])
     "\n      </tbody>\n    </table>\n    </div>\n"
     "    <p class=\"note\">HARD hold は人間が上書きできません。"
     "<code>:delivery/dispatch</code> と <code>:invoice/settle</code> は "
     "<code>wastetrade.phase</code> のどの phase の <code>:auto</code> にも入っておらず、"
     "governor の high-stakes ゲートとあわせて<strong>二重に</strong>人間承認を強制します。</p>\n"
     "  </section>\n"

     (section "Waste orders — 取引先デューデリジェンス"
              (str "seed 済み waste-order を <code>wastetrade.store/all-waste-orders</code> "
                   "から読み出したもの。信用審査 / 契約 / 制裁スクリーニングは governor が "
                   "出荷・請求の時点で直接読む ground truth です。")
              ["id" "order-id" "取引先" "廃棄物区分" "法域" "信用審査"
               "契約条項" "制裁 SC" "出荷 / 請求番号" "最後の op"]
              (map (partial order-row ledger) orders))

     (section "越境移動の事前同意 (PIC) — 二国間チェック"
              (str "この分野固有の、他の卸売 sibling に類例が無いチェック。"
                   "有害廃棄物該当の荷口に対してのみ、越境移動事前通報と<strong>仕向国当局の"
                   "事前同意</strong>の双方を要求します。仕向国の根拠は "
                   "<code>wastetrade.facts/consent-basis</code> 由来です。")
              ["id" "廃棄物区分" "仕向国" "Basel 条約" "仕向国の権限ある当局"
               "事前通報" "仕向国の同意" "governor 判定"]
              (map (partial pic-row ledger) orders))

     (section "Governor rule contract — 契約と実測"
              (str "<code>wastetrade.governor</code> が公開している 8 つの HARD rule。"
                   "「この実行で発火」列と発火回数は台帳から導出しています。"
                   "ビルドは全 rule が発火しなければ失敗します (<code>-main</code> の不変条件)。")
              ["rule" "対象 op" "何を再検証するか" "この実行" "hold 数"]
              (map (partial rule-row ledger) governor-rules))

     (section "HARD holds — この実行で実際に止めたもの"
              (str "台帳の <code>:governor-hold</code> のうち rule 違反を伴うもの。"
                   "detail 文字列は governor が生成したもので、ページ側では加工していません。")
              ["#" "op" "waste-order" "rule" "governor の detail" "confidence"]
              (map-indexed hold-row hard))

     (section "Rollout phase gate (0→3)"
              (str "<code>wastetrade.phase/phases</code> をそのまま描画したもの。"
                   "この実行は phase 3 (青い行) を既定に使い、比較のため phase 0 でも "
                   "<code>:order/intake</code> を 1 回投げています "
                   "(governor は clean、phase gate が <code>:phase-disabled</code> で hold)。")
              ["phase" "label" "書き込み可能な op" "自動 commit 可能な op"]
              (map (partial phase-row (:phase supervisor)) (sort-by key phase/phases)))

     (section "承認と帰属 (approver attribution) — 実測"
              (str "承認者 ID は graph の <code>:audit</code> チャネル "
                   "(<code>:approval-granted</code> の <code>:by</code>) にあります。"
                   "右端の列は<strong>ページ生成時に store の実レコードのキーを走査して</strong>"
                   "判定しており、store 側が後日 <code>:approved-by</code> を保持するように"
                   "直れば、このページは次回ビルドで自動的に表示が変わります。")
              ["thread" "op" "waste-order" "escalate 理由" "結果" "承認者" "commit record 側"]
              (map attribution-row attribution))

     (if (seq lost)
       (str "  <section class=\"card\">\n"
            "    <p class=\"note defect\"><strong>実測した store の挙動:</strong> "
            (count lost) " 件の承認済み actuation で、承認者 ID が commit record に残っていません。"
            "<code>wastetrade.operation/commit-record</code> は <code>:payload</code> に "
            "<code>:approved-by</code> を載せますが、<code>wastetrade.store</code> の "
            "<code>:order/mark-dispatched</code> / <code>:order/mark-invoiced</code> 分岐は "
            "<code>value</code> も <code>payload</code> も読まず、<code>wastetrade.registry</code> の "
            "レコードだけを保存します (<code>:consent-assessment/set</code> は "
            "<code>payload</code> を保存するので、そちらでは承認者が残ります)。"
            "したがって上表の「承認者」は<strong>監査台帳側からの join であり、"
            "commit record には無い</strong> — 「誰も承認していない」ことの表示ではありません。</p>\n"
            "  </section>\n")
       "")

     (section "法域 spec-basis カバレッジ"
              (str (esc (:note cov))
                   " 未登録の法域は正直に未登録と報告し、要件を創作しません。")
              ["ISO3" "この実行" "所管当局" "法的根拠" "出典"]
              (map (partial jurisdiction-row seeded-j) all-j))

     (section "監査台帳 (この実行)"
              (str "append-only の意思決定 fact 列。commit / HARD hold / phase gate hold / "
                   "人間による却下がすべて同じ台帳に並びます。")
              ["fact" "op" "waste-order" "disposition" "basis"]
              (map ledger-row ledger))

     (section "出荷 / 請求レジスタ (draft, unsigned)"
              (str "<code>wastetrade.registry</code> が構築した book-of-record ドラフト。"
                   "実在の計量器・税関・ERP・請求システムには一切接続しません。"
                   "署名は事業者の行為であって、このアクターの行為ではありません。")
              ["種別" "record_id" "waste-order" "法域" "状態"]
              (concat (map (partial register-row "waste-dispatch") (store/dispatch-history db))
                      (map (partial register-row "waste-invoice") (store/invoice-history db))))

     "</main>\n"
     "<footer>\n"
     "  <p>生成: <code>clojure -M:dev:render-html</code> "
     "(<code>wastetrade.render-html</code>) · "
     "決定論的 (タイムスタンプ・乱数なし、同じ seed に対して byte-identical) · "
     "スタイルはデジタル庁デザインシステム (DADS) のプリミティブを "
     "<code>docs/index.html</code> の vendored トークンから引用。</p>\n"
     "</footer>\n"
     "</body></html>\n")))

;; ----------------------------- entry point -----------------------------

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [db threads] :as run} (run-demo!)
        ledger (vec (store/ledger db))
        holds (governor-holds ledger)
        hard (hard-holds ledger)
        rules (observed-rules ledger)
        expected (into #{} (map first) governor-rules)
        missing (vec (sort (remove rules expected)))]

    ;; BUILD-TIME INVARIANTS -- a console with no holds on it is not a
    ;; demonstration of a governor, so refuse to write one.
    (when (zero? (count holds))
      (throw (ex-info "render-html: the run produced ZERO :governor-hold records -- refusing to write a console that shows no governor"
                      {:ledger-facts (count ledger)})))
    (when (zero? (count hard))
      (throw (ex-info "render-html: the run produced governor holds but NONE carried a rule violation -- refusing to write"
                      {:holds (count holds)})))
    (when (seq missing)
      (throw (ex-info (str "render-html: governor rules declared in `governor-rules` but never exercised: "
                           (str/join ", " (map str missing)))
                      {:missing missing :observed (vec (sort rules))})))
    (when (empty? (store/dispatch-history db))
      (throw (ex-info "render-html: no waste-dispatch register was produced -- the clean lifecycle did not commit"
                      {})))

    (spit out (render run) :encoding "UTF-8")
    (println "wrote" out)
    (println "  waste-orders     :" (count (store/all-waste-orders db)))
    (println "  ledger facts     :" (count ledger))
    (println "  governor holds   :" (count holds)
             (str "(HARD " (count hard) ", phase-gate " (count (phase-holds ledger)) ")"))
    (println "  distinct rules   :" (count (into #{} (mapcat :basis) hard)) "/" (count expected)
             (vec (sort (map str (into #{} (mapcat :basis) hard)))))
    (println "  approver rejects :" (count (rejection-holds ledger)))
    (println "  threads          :" (count threads))
    (println "  dispatch drafts  :" (count (store/dispatch-history db)))
    (println "  invoice drafts   :" (count (store/invoice-history db)))))
