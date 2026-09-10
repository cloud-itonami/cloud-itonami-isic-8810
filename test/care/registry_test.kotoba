(ns care.registry-test
  (:require [clojure.test :refer [deftest is]]
            [care.registry :as r]))

;; ----------------------------- caregiver-workload-exceeds-maximum? -----------------------------

(deftest not-exceeded-when-within-max-caseload
  (is (not (r/caregiver-workload-exceeds-maximum? {:caregiver-current-caseload 5 :caregiver-max-caseload 8})))
  (is (not (r/caregiver-workload-exceeds-maximum? {:caregiver-current-caseload 8 :caregiver-max-caseload 8}))))

(deftest exceeded-when-over-max-caseload
  (is (r/caregiver-workload-exceeds-maximum? {:caregiver-current-caseload 10 :caregiver-max-caseload 8}))
  (is (r/caregiver-workload-exceeds-maximum? {:caregiver-current-caseload 9 :caregiver-max-caseload 8})))

(deftest exceeded-is-false-on-missing-fields
  (is (not (r/caregiver-workload-exceeds-maximum? {})))
  (is (not (r/caregiver-workload-exceeds-maximum? {:caregiver-current-caseload 10}))))

;; ----------------------------- register-checkin-dispatch -----------------------------

(deftest dispatch-is-a-draft-not-a-real-dispatch
  (let [result (r/register-checkin-dispatch "case-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest dispatch-assigns-dispatch-number
  (let [result (r/register-checkin-dispatch "case-1" "JPN" 7)]
    (is (= (get result "dispatch_number") "JPN-CHK-000007"))
    (is (= (get-in result ["record" "case_id"]) "case-1"))
    (is (= (get-in result ["record" "kind"]) "checkin-dispatch-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest dispatch-validation-rules
  (is (thrown? Exception (r/register-checkin-dispatch "" "JPN" 0)))
  (is (thrown? Exception (r/register-checkin-dispatch "case-1" "" 0)))
  (is (thrown? Exception (r/register-checkin-dispatch "case-1" "JPN" -1))))

;; ----------------------------- register-case-closure -----------------------------

(deftest closure-is-a-draft-not-a-real-closure
  (let [result (r/register-case-closure "case-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest closure-assigns-closure-number
  (let [result (r/register-case-closure "case-1" "JPN" 3)]
    (is (= (get result "closure_number") "JPN-CLS-000003"))
    (is (= (get-in result ["record" "case_id"]) "case-1"))
    (is (= (get-in result ["record" "kind"]) "case-closure-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest closure-validation-rules
  (is (thrown? Exception (r/register-case-closure "" "JPN" 0)))
  (is (thrown? Exception (r/register-case-closure "case-1" "" 0)))
  (is (thrown? Exception (r/register-case-closure "case-1" "JPN" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-checkin-dispatch "case-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-checkin-dispatch "case-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-CHK-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-CHK-000001" (get-in hist2 [1 "record_id"])))))
