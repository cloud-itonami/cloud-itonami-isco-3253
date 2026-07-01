(ns community-health.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [community-health.store :as store]
            [community-health.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-resident! st {:resident-id "res-1" :consented-at "2026-01-01"})
    st))

(deftest proceeds-on-clean-screening
  (let [st (fresh-store)
        env (governor/env-for-store st)
        proposal {:action :screen :resident-id "res-1" :safety-class :low
                   :effect :propose :confidence 0.9}]
    (is (= :proceed (:decision (governor/assess env proposal))))))

(deftest holds-on-unregistered-resident
  (let [st (fresh-store)
        env (governor/env-for-store st)
        proposal {:action :screen :resident-id "no-such-resident" :safety-class :low
                   :effect :propose :confidence 0.9}
        result (governor/assess env proposal)]
    (is (= :hold (:decision result)))
    (is (some #(= :no-resident (:rule %)) (:violations result)))))

(deftest holds-on-no-actuation-violation
  (let [st (fresh-store)
        env (governor/env-for-store st)
        proposal {:action :screen :resident-id "res-1" :safety-class :low
                   :effect :direct-write :confidence 0.9}
        result (governor/assess env proposal)]
    (is (= :hold (:decision result)))
    (is (some #(= :no-actuation (:rule %)) (:violations result)))))

(deftest urgent-risk-always-escalates-even-when-clean-and-confident
  (let [st (fresh-store)
        env (governor/env-for-store st)
        proposal {:action :refer :resident-id "res-1" :safety-class :none
                   :effect :propose :confidence 1.0 :urgent? true}
        result (governor/assess env proposal)]
    (is (= :human-approval (:decision result)))
    (is (= :urgent-risk (:reason result)))))

(deftest human-approval-on-high-safety-class-even-when-clean
  (let [st (fresh-store)
        env (governor/env-for-store st)
        proposal {:action :refer :resident-id "res-1" :safety-class :high
                   :effect :propose :confidence 0.9}]
    (is (= :human-approval (:decision (governor/assess env proposal))))))

(deftest human-approval-on-low-confidence
  (let [st (fresh-store)
        env (governor/env-for-store st)
        proposal {:action :screen :resident-id "res-1" :safety-class :none
                   :effect :propose :confidence 0.2}
        result (governor/assess env proposal)]
    (is (= :human-approval (:decision result)))
    (is (= :low-confidence (:reason result)))))

(deftest store-records-append-only
  (let [st (fresh-store)]
    (store/record-screening! st {:screening-id "s1" :resident-id "res-1" :result :normal})
    (store/record-referral! st {:referral-id "r1" :resident-id "res-1" :provider "clinic-a" :urgent? false})
    (is (= 1 (count (store/screenings-of st "res-1"))))
    (is (= 1 (count (store/referrals-of st "res-1"))))
    (is (empty? (store/screenings-of st "res-2")))))
