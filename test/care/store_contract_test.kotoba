(ns care.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a
  configuration change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the
  sibling actor."
  (:require [clojure.test :refer [deftest is testing]]
            [care.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Sato Kenji" (:recipient-name (store/case-of s "case-1"))))
      (is (= "JPN" (:jurisdiction (store/case-of s "case-1"))))
      (is (= 5 (:caregiver-current-caseload (store/case-of s "case-1"))))
      (is (= 8 (:caregiver-max-caseload (store/case-of s "case-1"))))
      (is (false? (:safeguarding-signal-unresolved? (store/case-of s "case-1"))))
      (is (= 10 (:caregiver-current-caseload (store/case-of s "case-3"))))
      (is (true? (:safeguarding-signal-unresolved? (store/case-of s "case-4"))))
      (is (false? (:checkin-dispatched? (store/case-of s "case-1"))))
      (is (false? (:case-closed? (store/case-of s "case-1"))))
      (is (= ["case-1" "case-2" "case-3" "case-4"]
             (mapv :id (store/all-cases s))))
      (is (nil? (store/safeguarding-screen-of s "case-1")))
      (is (nil? (store/careplan-of s "case-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/dispatch-history s)))
      (is (= [] (store/closure-history s)))
      (is (zero? (store/next-dispatch-sequence s "JPN")))
      (is (zero? (store/next-closure-sequence s "JPN")))
      (is (false? (store/case-already-dispatched? s "case-1")))
      (is (false? (store/case-already-closed? s "case-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :case/upsert
                                 :value {:id "case-1" :recipient-name "Sato Kenji"}})
        (is (= "Sato Kenji" (:recipient-name (store/case-of s "case-1"))))
        (is (= 8 (:caregiver-max-caseload (store/case-of s "case-1"))) "unrelated field preserved"))
      (testing "careplan / safeguarding-screen payloads commit and read back"
        (store/commit-record! s {:effect :careplan/set :path ["case-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/careplan-of s "case-1")))
        (store/commit-record! s {:effect :safeguarding-screen/set :path ["case-1"]
                                 :payload {:case-id "case-1" :verdict :resolved}})
        (is (= {:case-id "case-1" :verdict :resolved} (store/safeguarding-screen-of s "case-1"))))
      (testing "check-in dispatch drafts a record and advances the sequence"
        (store/commit-record! s {:effect :case/mark-dispatched :path ["case-1"]})
        (is (= "JPN-CHK-000000" (get (first (store/dispatch-history s)) "record_id")))
        (is (= "checkin-dispatch-draft" (get (first (store/dispatch-history s)) "kind")))
        (is (true? (:checkin-dispatched? (store/case-of s "case-1"))))
        (is (= 1 (count (store/dispatch-history s))))
        (is (= 1 (store/next-dispatch-sequence s "JPN")))
        (is (true? (store/case-already-dispatched? s "case-1")))
        (is (false? (store/case-already-dispatched? s "case-2"))))
      (testing "case closure drafts a record and advances the sequence"
        (store/commit-record! s {:effect :case/mark-closed :path ["case-1"]})
        (is (= "JPN-CLS-000000" (get (first (store/closure-history s)) "record_id")))
        (is (= "case-closure-draft" (get (first (store/closure-history s)) "kind")))
        (is (true? (:case-closed? (store/case-of s "case-1"))))
        (is (= 1 (count (store/closure-history s))))
        (is (= 1 (store/next-closure-sequence s "JPN")))
        (is (true? (store/case-already-closed? s "case-1")))
        (is (false? (store/case-already-closed? s "case-2"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/case-of s "nope")))
    (is (= [] (store/all-cases s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/dispatch-history s)))
    (is (= [] (store/closure-history s)))
    (is (zero? (store/next-dispatch-sequence s "JPN")))
    (is (zero? (store/next-closure-sequence s "JPN")))
    (store/with-cases s {"x" {:id "x" :recipient-name "n"
                             :caregiver-current-caseload 5 :caregiver-max-caseload 8
                             :safeguarding-signal-unresolved? false
                             :checkin-dispatched? false :case-closed? false
                             :jurisdiction "JPN" :status :intake}})
    (is (= "n" (:recipient-name (store/case-of s "x"))))))
