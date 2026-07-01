(ns community-health.governor
  "CommunityHealthGovernor — the independent safety/traceability layer for
  the ISCO-08 3253 independent community-health-outreach actor. The
  Outreach Advisor proposes actions (screen, refer); it has no notion of
  resident consent provenance or urgent-risk escalation, so this MUST be a
  separate system able to *reject* a proposal and fall back to HOLD — the
  itonami-actor pattern (independent Governor gates a proposing actor)
  applied to this occupation.

  Charter (mirrors ADR-2607011000 robotics premise + ADR-2607012000
  cloud-itonami-isco): the actor never dispatches a robot action or writes an
  operating record the governor refuses. A referral flagged `:urgent? true`
  ALWAYS requires human sign-off — this cannot be suppressed, the itonami
  analog of a no-diagnosis-by-LLM charter.

  HARD invariants for :outreach/propose:
    1. Resident consent provenance — a screening or referral must
       reference a registered (consented) resident.
    2. No-actuation               — the proposal must not directly mutate
       a screening or referral record outside the record-screening!/
       record-referral! path (effect must be :propose, never a raw store
       write).
    3. Urgent-risk escalation      — a referral flagged `:urgent? true`
       can never be `:proceed`; it is always routed to `:human-approval`,
       regardless of safety-class or confidence.
  SOFT:
    4. Confidence floor → escalate."
  (:require [community-health.store :as store]))

(def confidence-floor 0.6)
(def safety-classes [:none :low :medium :high :safety-critical])

(defn- safety-rank [safety-class]
  (let [idx (.indexOf safety-classes safety-class)]
    (if (neg? idx) 0 idx)))

(defn- hard-violations [{:keys [resident-fn]} proposal]
  (let [{:keys [resident-id effect]} proposal
        found-resident (resident-fn resident-id)]
    (cond-> []
      (nil? found-resident)
      (conj {:rule :no-resident :detail (str "未登録/未同意 resident " resident-id)})

      (not= :propose effect)
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"}))))

(defn assess
  "Assess a proposal against `env` (a map with `:resident-fn` lookup,
  decoupled from any concrete Store so this stays pure). Returns
  `{:decision :proceed|:hold|:human-approval :violations [...] :confidence n}`."
  [env proposal]
  (let [violations (hard-violations env proposal)
        safety-class (or (:safety-class proposal) :none)
        confidence (or (:confidence proposal) 1.0)
        urgent? (boolean (:urgent? proposal))]
    (cond
      (seq violations)
      {:decision :hold :violations violations :confidence confidence}

      urgent?
      {:decision :human-approval :violations [] :confidence confidence
       :reason :urgent-risk}

      (>= (safety-rank safety-class) (safety-rank :high))
      {:decision :human-approval :violations [] :confidence confidence}

      (< confidence confidence-floor)
      {:decision :human-approval :violations [] :confidence confidence
       :reason :low-confidence}

      :else
      {:decision :proceed :violations [] :confidence confidence})))

(defn env-for-store
  "Build the decoupled env map `assess` needs from a concrete
  `community-health.store/Store` implementation."
  [store]
  {:resident-fn #(store/resident store %)})
