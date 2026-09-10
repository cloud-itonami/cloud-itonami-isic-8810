(ns care.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:actuation/dispatch-checkin`/`:actuation/close-case`
  must NEVER be a member of any phase's `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [care.phase :as phase]))

(deftest dispatch-checkin-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real check-in dispatch"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/dispatch-checkin))
          (str "phase " n " must not auto-commit :actuation/dispatch-checkin")))))

(deftest close-case-never-auto-at-any-phase
  (testing "structural invariant: no phase auto-commits a real case closure"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/close-case))
          (str "phase " n " must not auto-commit :actuation/close-case")))))

(deftest safeguarding-screen-never-auto-at-any-phase
  (testing "screening carries no direct capital risk, but is still never auto-eligible, matching every sibling screening op in this fleet"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :safeguarding/screen))
          (str "phase " n " must not auto-commit :safeguarding/screen")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":case/intake carries no direct capital risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:case/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :case/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/dispatch-checkin} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/close-case} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :case/intake} :commit)))))
