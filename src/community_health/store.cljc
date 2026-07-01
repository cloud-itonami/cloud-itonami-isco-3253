(ns community-health.store
  "SSoT for the ISCO-08 3253 independent community-health-outreach
  sole-proprietor actor, behind a `Store` protocol so the backend is a swap
  (MemStore default ‖ a real Datomic/kotoba-server backend, per the itonami
  actor pattern).

  Domain = independent community health outreach practice:

    resident   — a consented outreach recipient (residentId, consentedAt)
    screening  — a health screening event (screeningId, residentId, result
                 #{:normal :flagged})
    referral   — a referral to a care provider (referralId, residentId,
                 provider, urgent? boolean)

  The append-only records are the operating ledger: a screening or referral
  must reference a registered (consented) resident, and screenings/
  referrals are never mutated in place, only appended.")

(defprotocol Store
  (resident [st resident-id])
  (screenings-of [st resident-id])
  (referrals-of [st resident-id])
  (register-resident! [st resident])
  (record-screening! [st screening])
  (record-referral! [st referral]))

(defrecord MemStore [state]
  Store
  (resident [_ resident-id]
    (get-in @state [:residents resident-id]))
  (screenings-of [_ resident-id]
    (filter #(= resident-id (:resident-id %)) (:screenings @state)))
  (referrals-of [_ resident-id]
    (filter #(= resident-id (:resident-id %)) (:referrals @state)))
  (register-resident! [_ resident]
    (swap! state assoc-in [:residents (:resident-id resident)] resident))
  (record-screening! [_ screening]
    (swap! state update :screenings (fnil conj []) screening))
  (record-referral! [_ referral]
    (swap! state update :referrals (fnil conj []) referral)))

(defn mem-store
  ([] (mem-store {}))
  ([seed]
   (->MemStore (atom (merge {:residents {} :screenings [] :referrals []} seed)))))
