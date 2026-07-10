(ns wastetrade.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a
  configuration change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the
  sibling actor."
  (:require [clojure.test :refer [deftest is testing]]
            [wastetrade.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "JPN" (:jurisdiction (store/waste-order s "wo-1"))))
      (is (= "DEU" (:destination-country (store/waste-order s "wo-1"))))
      (is (= "Kestrel Recycling & Recovery GmbH" (:counterparty (store/waste-order s "wo-1"))))
      (is (= "waste electrical and electronic equipment (WEEE / e-waste)" (:waste-stream-type (store/waste-order s "wo-1"))))
      (is (= "ATL" (:jurisdiction (store/waste-order s "wo-2"))))
      (is (false? (:credit-cleared? (store/waste-order s "wo-3"))) "wo-3 credit not cleared")
      (is (nil? (:contract-terms (store/waste-order s "wo-4"))) "wo-4 no contract-terms")
      (is (false? (:sanctions-screened? (store/waste-order s "wo-5"))) "wo-5 sanctions not screened")
      (is (= "used lead-acid batteries (ULAB)" (:waste-stream-type (store/waste-order s "wo-6"))))
      (is (= "USA" (:destination-country (store/waste-order s "wo-6"))))
      (is (false? (:transboundary-notification-filed? (store/waste-order s "wo-6"))) "wo-6 notification not filed")
      (is (false? (:destination-country-consent-documented? (store/waste-order s "wo-6"))) "wo-6 destination consent undocumented")
      (is (= "ferrous scrap metal (sorted, HS 7204)" (:waste-stream-type (store/waste-order s "wo-7"))))
      (is (false? (:transboundary-notification-filed? (store/waste-order s "wo-7"))) "wo-7 same unverified facts as wo-6, different (non-hazardous) waste stream")
      (is (true? (:transboundary-notification-filed? (store/waste-order s "wo-8"))) "wo-8 notification filed")
      (is (false? (:destination-country-consent-documented? (store/waste-order s "wo-8"))) "wo-8 destination consent still undocumented (partial satisfaction)")
      (is (false? (:dispatched? (store/waste-order s "wo-1"))))
      (is (false? (:invoiced? (store/waste-order s "wo-1"))))
      (is (= ["wo-1" "wo-2" "wo-3" "wo-4" "wo-5" "wo-6" "wo-7" "wo-8"]
             (mapv :id (store/all-waste-orders s))))
      (is (nil? (store/assessment-of s "wo-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/dispatch-history s)))
      (is (= [] (store/invoice-history s)))
      (is (zero? (store/next-dispatch-sequence s "JPN")))
      (is (zero? (store/next-invoice-sequence s "JPN")))
      (is (false? (store/waste-order-already-dispatched? s "wo-1")))
      (is (false? (store/waste-order-already-invoiced? s "wo-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :order/upsert
                                 :value {:id "wo-1" :counterparty "Kestrel Recycling & Recovery GmbH"}})
        (is (= "Kestrel Recycling & Recovery GmbH" (:counterparty (store/waste-order s "wo-1"))))
        (is (= "JPN" (:jurisdiction (store/waste-order s "wo-1"))) "unrelated field preserved"))
      (testing "consent-assessment payloads commit and read back"
        (store/commit-record! s {:effect :consent-assessment/set :path ["wo-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/assessment-of s "wo-1"))))
      (testing "waste dispatch drafts a record and advances the dispatch sequence"
        (store/commit-record! s {:effect :order/mark-dispatched :path ["wo-1"]})
        (is (= "JPN-DISPATCH-000000" (get (first (store/dispatch-history s)) "record_id")))
        (is (= "waste-dispatch-draft" (get (first (store/dispatch-history s)) "kind")))
        (is (true? (:dispatched? (store/waste-order s "wo-1"))))
        (is (= 1 (count (store/dispatch-history s))))
        (is (= 1 (store/next-dispatch-sequence s "JPN")))
        (is (true? (store/waste-order-already-dispatched? s "wo-1"))))
      (testing "invoice settlement drafts a record and advances the invoice sequence"
        (store/commit-record! s {:effect :order/mark-invoiced :path ["wo-1"]})
        (is (= "JPN-INVOICE-000000" (get (first (store/invoice-history s)) "record_id")))
        (is (= "waste-invoice-draft" (get (first (store/invoice-history s)) "kind")))
        (is (true? (:invoiced? (store/waste-order s "wo-1"))))
        (is (= 1 (count (store/invoice-history s))))
        (is (= 1 (store/next-invoice-sequence s "JPN")))
        (is (true? (store/waste-order-already-invoiced? s "wo-1"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/waste-order s "nope")))
    (is (= [] (store/all-waste-orders s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/dispatch-history s)))
    (is (= [] (store/invoice-history s)))
    (is (zero? (store/next-dispatch-sequence s "JPN")))
    (is (zero? (store/next-invoice-sequence s "JPN")))
    (store/with-waste-orders s {"x" {:id "x" :order-id "WO-X" :waste-stream-type "ferrous scrap metal (sorted, HS 7204)"
                                     :counterparty "c" :price 4500.00
                                     :contract-terms "CIF, net 30 days"
                                     :credit-cleared? true :sanctions-screened? true
                                     :transboundary-notification-filed? true
                                     :destination-country-consent-documented? true
                                     :dispatched? false :invoiced? false
                                     :jurisdiction "JPN" :destination-country "DEU" :status :intake
                                     :dispatch-number nil :invoice-number nil}})
    (is (= "c" (:counterparty (store/waste-order s "x"))))))
