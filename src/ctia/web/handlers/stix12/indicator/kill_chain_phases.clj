(ns ctia.web.handlers.stix12.indicator.kill-chain-phases
  (:import [org.mitre.stix.common_1
            KillChainPhaseReferenceType
            KillChainPhasesReferenceType]

           [org.mitre.stix.indicator_2
            Indicator]))

(defn kill-chain-phases? [indicator]
  (boolean (not-empty (:kill_chain_phases indicator))))

(defn attach-kill-chain-phases [^Indicator xml-indicator indicator]
  (.withKillChainPhases xml-indicator
   (-> (KillChainPhasesReferenceType.)
       (.withKillChainPhases
        (for [kcp (:kill_chain_phases indicator)]
          (.withName (KillChainPhaseReferenceType.) kcp))))))
