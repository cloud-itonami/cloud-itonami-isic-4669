(ns wastetrade.facts-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.set :as set]
            [wastetrade.facts :as facts]))

(deftest jpn-has-a-spec-basis
  (is (some? (facts/spec-basis "JPN")))
  (is (string? (:provenance (facts/spec-basis "JPN")))))

(deftest all-four-seeded-jurisdictions-have-required-evidence
  ;; every seeded waste-wholesale jurisdiction actually has a real
  ;; required-evidence set reported honestly here
  (doseq [iso3 ["JPN" "USA" "GBR" "DEU"]]
    (is (seq (facts/evidence-checklist iso3)) (str iso3 " required-evidence"))))

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest coverage-never-reports-a-missing-jurisdiction-as-covered
  (let [report (facts/coverage ["JPN" "ATL" "GBR"])]
    (is (= 2 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["GBR" "JPN"] (:covered-jurisdictions report)))))

(deftest required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "JPN")]
    (is (facts/required-evidence-satisfied? "JPN" all))
    (is (not (facts/required-evidence-satisfied? "JPN" (rest all))))
    (is (not (facts/required-evidence-satisfied? "ATL" all)) "no spec-basis -> never satisfied")))

;; ----------------------------- hazardous / green-list classification -----------------------------

(deftest hazardous-waste-streams-predicate
  (doseq [s ["waste electrical and electronic equipment (WEEE / e-waste)"
             "used lead-acid batteries (ULAB)"
             "hazardous chemical waste (spent solvents)"
             "used oil"
             "asbestos waste"]]
    (is (true? (facts/hazardous-waste-stream? s)) (str s " is hazardous")))
  (doseq [s ["ferrous scrap metal (sorted, HS 7204)"
             "non-ferrous scrap metal (sorted, clean)"
             "sorted plastics (single-resin, e.g. PET/HDPE)"
             "paper and cardboard (sorted, baled)"
             "textile waste (sorted, clean)"]]
    (is (false? (facts/hazardous-waste-stream? s)) (str s " is NOT hazardous"))))

(deftest green-list-streams-are-disjoint-from-hazardous
  (is (empty? (set/intersection facts/hazardous-waste-streams
                                facts/green-list-waste-streams))))

;; ----------------------------- Basel-party / consent-basis catalog -----------------------------

(deftest usa-is-not-a-basel-party
  (is (false? (get facts/basel-party? "USA")))
  (is (true? (get facts/basel-party? "JPN")))
  (is (true? (get facts/basel-party? "GBR")))
  (is (true? (get facts/basel-party? "DEU"))))

(deftest every-seeded-destination-has-a-binding-consent-basis
  ;; unlike the metal-wholesale sibling's OECD-Guidance non-statutory
  ;; fallback, every destination seeded here (Party OR the one seeded
  ;; non-Party) has a genuinely BINDING consent-from-destination regime
  (doseq [iso3 ["JPN" "USA" "GBR" "DEU"]]
    (is (true? (:binding? (facts/consent-citation iso3))) (str iso3 " consent-basis is binding"))))

(deftest usa-consent-basis-cites-rcra-not-basel
  (let [c (facts/consent-citation "USA")]
    (is (re-find #"RCRA|Resource Conservation" (:legal-basis c)))
    (is (re-find #"NOT a Party" (:legal-basis c)))))

(deftest basel-party-destinations-cite-basel-article-6
  (doseq [iso3 ["JPN" "GBR" "DEU"]]
    (is (re-find #"Basel Convention" (:legal-basis (facts/consent-citation iso3))))
    (is (re-find #"Article 6" (:legal-basis (facts/consent-citation iso3))))))

(deftest consent-citation-nil-for-unseeded-destination
  (is (nil? (facts/consent-citation "ATL"))))

;; ----------------------------- e-waste certification (informational only) -----------------------------

(deftest e-waste-certifications-are-never-binding
  (doseq [[_ v] facts/e-waste-certification-schemes]
    (is (false? (:binding? v)))
    (is (true? (:certification-scheme? v)))))

(deftest e-waste-order-predicate
  (is (true? (facts/e-waste-order? "waste electrical and electronic equipment (WEEE / e-waste)")))
  (is (false? (facts/e-waste-order? "used lead-acid batteries (ULAB)"))))
