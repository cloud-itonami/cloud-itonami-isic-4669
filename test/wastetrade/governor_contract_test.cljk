(ns wastetrade.governor-contract-test
  "The governor contract as executable tests. The single invariant
  under test:

    WasteTradeAdvisor never dispatches waste/scrap across a border to a
    counterparty or settles an invoice the Waste Trading Governor would
    reject, `:delivery/dispatch`/`:invoice/settle` NEVER auto-commit at
    any phase, `:order/intake` (no direct capital risk) MAY auto-commit
    when clean, and every decision (commit OR hold) leaves exactly one
    ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [wastetrade.store :as store]
            [wastetrade.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :trading-supervisor :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify!
  "Walks `subject` through consent verify -> approve, leaving a
  consent assessment on file. Uses distinct thread-ids per call site
  by suffixing `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :consent/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :order/intake :subject "wo-1"
                   :patch {:id "wo-1" :counterparty "Kestrel Recycling & Recovery GmbH"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Kestrel Recycling & Recovery GmbH" (:counterparty (store/waste-order db "wo-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest consent-verify-always-needs-approval
  (testing "consent verify is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :consent/verify :subject "wo-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/assessment-of db "wo-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a consent/verify proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :consent/verify :subject "wo-2"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/assessment-of db "wo-2")) "no assessment written"))))

(deftest dispatch-without-assessment-is-held
  (testing "delivery/dispatch before any consent verification -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :delivery/dispatch :subject "wo-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest credit-uncleared-is-held-and-unoverridable
  (testing "a counterparty whose credit has not been cleared -> HOLD, and never reaches request-approval"
    (let [[db actor] (fresh)
          _ (verify! actor "t5pre" "wo-3")
          res (exec-op actor "t5" {:op :delivery/dispatch :subject "wo-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:credit-uncleared} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest contract-missing-is-held-and-unoverridable
  (testing "an order with no contract-terms on file -> HOLD, and never reaches request-approval"
    (let [[db actor] (fresh)
          _ (verify! actor "t6pre" "wo-4")
          res (exec-op actor "t6" {:op :delivery/dispatch :subject "wo-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:contract-missing} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest counterparty-sanctions-flag-unresolved-is-held-and-unoverridable
  (testing "a counterparty that has not passed OFAC / equivalent sanctions screening -> HOLD, and never reaches request-approval"
    (let [[db actor] (fresh)
          _ (verify! actor "t7pre" "wo-5")
          res (exec-op actor "t7" {:op :delivery/dispatch :subject "wo-5"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:counterparty-sanctions-flag-unresolved} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest prior-informed-consent-missing-is-held-and-unoverridable
  (testing "a hazardous waste-order (used lead-acid batteries, wo-6) destined for the USA -- a Basel non-Party -- with NEITHER a filed transboundary notification NOR a documented destination-country consent -> HOLD, and never reaches request-approval -- the domain-defining check, proving it fires regardless of the destination's Basel-Party status"
    (let [[db actor] (fresh)
          _ (verify! actor "t8pre" "wo-6")
          res (exec-op actor "t8" {:op :delivery/dispatch :subject "wo-6"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:prior-informed-consent-missing} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest prior-informed-consent-check-is-a-no-op-for-green-list-waste
  (testing "sorted ferrous scrap (wo-7) carries the SAME undocumented PIC facts as wo-6 (used lead-acid batteries), but sorted ferrous scrap is NOT a hazardous stream in this actor's scope -> the check does NOT fire, dispatch still always escalates for the usual human sign-off -- proving the check is genuinely type-gated, not a blanket PIC requirement"
    (let [[db actor] (fresh)
          _ (verify! actor "t9pre" "wo-7")
          r1 (exec-op actor "t9" {:op :delivery/dispatch :subject "wo-7"} operator)]
      (is (= :interrupted (:status r1)) "pauses for the ordinary human dispatch sign-off, NOT a prior-informed-consent hold")
      (let [r2 (approve! actor "t9")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:dispatched? (store/waste-order db "wo-7"))))
        (is (= 1 (count (store/dispatch-history db))))))))

(deftest prior-informed-consent-requires-both-sub-facts
  (testing "wo-8 (hazardous chemical waste) has the transboundary notification FILED but destination-country consent NOT documented -> STILL HOLD -- proving both sub-facts are required, partial satisfaction does not suffice"
    (let [[db actor] (fresh)
          _ (verify! actor "t10pre" "wo-8")
          res (exec-op actor "t10" {:op :delivery/dispatch :subject "wo-8"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (not= :interrupted (:status res)))
      (is (some #{:prior-informed-consent-missing} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest dispatch-always-escalates-then-human-decides
  (testing "a clean, fully-verified, credit-cleared, contract-on-file, consent-verified, sanctions-screened hazardous order still ALWAYS interrupts for human approval -- :delivery/dispatch is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t11pre" "wo-1")
          r1 (exec-op actor "t11" {:op :delivery/dispatch :subject "wo-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, dispatch record drafted"
        (let [r2 (approve! actor "t11")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:dispatched? (store/waste-order db "wo-1"))))
          (is (= 1 (count (store/dispatch-history db))) "one draft dispatch record"))))))

(deftest invoice-settle-always-escalates-then-human-decides
  (testing "a clean, fully-verified, already-dispatched order still ALWAYS interrupts for human approval -- :invoice/settle is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t12pre" "wo-1")
          _ (exec-op actor "t12dispatch" {:op :delivery/dispatch :subject "wo-1"} operator)
          _ (approve! actor "t12dispatch")
          r1 (exec-op actor "t12" {:op :invoice/settle :subject "wo-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, invoice record drafted"
        (let [r2 (approve! actor "t12")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:invoiced? (store/waste-order db "wo-1"))))
          (is (= 1 (count (store/invoice-history db))) "one draft invoice record"))))))

(deftest delivery-dispatch-double-dispatch-is-held
  (testing "dispatching the same waste-order twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t13pre" "wo-1")
          _ (exec-op actor "t13a" {:op :delivery/dispatch :subject "wo-1"} operator)
          _ (approve! actor "t13a")
          res (exec-op actor "t13" {:op :delivery/dispatch :subject "wo-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-dispatched} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/dispatch-history db))) "still only the one earlier dispatch"))))

(deftest invoice-settle-double-invoice-is-held
  (testing "settling the same waste-order's invoice twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t14pre" "wo-1")
          _ (exec-op actor "t14dispatch" {:op :delivery/dispatch :subject "wo-1"} operator)
          _ (approve! actor "t14dispatch")
          _ (exec-op actor "t14a" {:op :invoice/settle :subject "wo-1"} operator)
          _ (approve! actor "t14a")
          res (exec-op actor "t14" {:op :invoice/settle :subject "wo-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-invoiced} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/invoice-history db))) "still only the one earlier invoice"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :order/intake :subject "wo-1"
                          :patch {:id "wo-1" :counterparty "Kestrel Recycling & Recovery GmbH"}} operator)
      (exec-op actor "b" {:op :consent/verify :subject "wo-2"} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
