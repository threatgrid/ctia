(ns ctia.test-helpers.fixtures
  (:require [ctia.entity.investigation.examples :refer [investigation-maximal investigation-minimal]]
            [ctim.domain.id :refer [make-transient-id]]
            [ctim.examples
             [actors :refer [actor-maximal actor-minimal]]
             [assets :refer [asset-maximal asset-minimal]]
             [asset-mappings :refer [asset-mapping-maximal asset-mapping-minimal]]
             [asset-properties :refer [asset-properties-maximal asset-properties-minimal]]
             [target-records :refer [target-record-maximal target-record-minimal]]
             [attack-patterns :refer [attack-pattern-maximal attack-pattern-minimal]]
             [campaigns :refer [campaign-maximal campaign-minimal]]
             [coas :refer [coa-maximal coa-minimal]]
             [incidents :refer [incident-maximal incident-minimal]]
             [indicators :refer [indicator-maximal indicator-minimal]]
             [judgements :refer [judgement-maximal judgement-minimal]]
             [malwares :refer [malware-maximal malware-minimal]]
             [relationships :refer [relationship-maximal relationship-minimal]]
             [casebooks :refer [casebook-maximal casebook-minimal]]
             [sightings :refer [sighting-maximal sighting-minimal]]
             [tools :refer [tool-maximal tool-minimal]]
             [vulnerabilities :refer [vulnerability-maximal vulnerability-minimal]]
             [weaknesses :refer [weakness-maximal weakness-minimal]]]))

(defn randomize [doc]
  (assoc doc
         :id (make-transient-id "_")))

(defn n-doc [fixture nb]
  (map randomize (repeat nb fixture)))

(defn n-examples
  [entity-type nb maximal?]
  (case entity-type
    :actor            (n-doc (if maximal? actor-maximal actor-minimal) nb)
    :asset            (n-doc (if maximal? asset-maximal asset-minimal) nb)
    :asset-mapping    (n-doc (if maximal? asset-mapping-maximal asset-mapping-minimal) nb)
    :asset-properties (n-doc (if maximal? asset-properties-maximal asset-properties-minimal) nb)
    :attack-pattern   (n-doc (if maximal? attack-pattern-maximal attack-pattern-minimal) nb)
    :campaign         (n-doc (if maximal? campaign-maximal campaign-minimal) nb)
    :coa              (n-doc (if maximal? coa-maximal coa-minimal) nb)
    :incident         (n-doc (if maximal? incident-maximal incident-minimal) nb)
    :indicator        (n-doc (if maximal? indicator-maximal indicator-minimal) nb)
    :investigation    (n-doc (if maximal? investigation-maximal investigation-minimal) nb)
    :judgement        (n-doc (if maximal? judgement-maximal judgement-minimal) nb)
    :malware          (n-doc (if maximal? malware-maximal malware-minimal) nb)
    :relationship     (n-doc (if maximal? relationship-maximal relationship-minimal) nb)
    :casebook         (n-doc (if maximal? casebook-maximal casebook-minimal) nb)
    :sighting         (n-doc (if maximal? sighting-maximal sighting-minimal) nb)
    :target-record    (n-doc (if maximal? target-record-maximal target-record-minimal) nb)
    :tool             (n-doc (if maximal? tool-maximal tool-minimal) nb)
    :vulnerability    (n-doc (if maximal? vulnerability-maximal vulnerability-minimal) nb)
    :weakness         (n-doc (if maximal? weakness-maximal weakness-minimal) nb)))

(defn bundle
  [fixtures-nb maximal?]
  (let [;; it is important to set relationship between Assets and
        ;; AssetMappings/AssetProperties via :asset-ref field
        assets             (vec (n-examples :asset fixtures-nb maximal?))
        gen+set-asset-refs (fn [entity-key]
                             (->> (n-examples entity-key fixtures-nb maximal?)
                                  (map-indexed #(assoc
                                                 %2 :asset_ref
                                                 (-> assets (get %1) :id)))))]
    {:actors           (n-examples :actor fixtures-nb maximal?)
     :assets           assets
     :asset_mappings   (gen+set-asset-refs :asset-mapping)
     :asset_properties (gen+set-asset-refs :asset-properties)
     :attack_patterns  (n-examples :attack-pattern fixtures-nb maximal?)
     :campaigns        (n-examples :campaign fixtures-nb maximal?)
     :coas             (n-examples :coa fixtures-nb maximal?)
     :incidents        (n-examples :incident fixtures-nb maximal?)
     :indicators       (n-examples :indicator fixtures-nb maximal?)
     :investigations   (n-examples :investigation fixtures-nb maximal?)
     :judgements       (n-examples :judgement fixtures-nb maximal?)
     :malwares         (n-examples :malware fixtures-nb maximal?)
     :relationships    (n-examples :relationship fixtures-nb maximal?)
     :casebooks        (n-examples :casebook fixtures-nb maximal?)
     :sightings        (n-examples :sighting fixtures-nb maximal?)
     :target_records   (n-examples :target-record fixtures-nb maximal?)
     :tools            (n-examples :tool fixtures-nb maximal?)
     :vulnerabilities  (n-examples :vulnerability fixtures-nb maximal?)
     :weaknesses       (n-examples :weakness fixtures-nb maximal?)}))

(defn relationship
  [rel-type source-id target-id]
  (assoc (randomize relationship-minimal)
         :source_ref source-id
         :target_ref target-id
         :relationship_type rel-type))

(defn mk-relationships
  [rel-type sources targets]
  (mapcat
   (fn [{source-id :id}]
     (map (fn [{target-id :id}]
            (relationship rel-type source-id target-id))
          targets))
   sources))

(defn threat-ctx-bundle
  "returns a threat context with 1 indicator, 3 related judgements and 2 attack patterns"
  ([] (threat-ctx-bundle false))
  ([maximal?]
   (let [indicators (n-examples :indicator 1 maximal?)
         attack-patterns (n-examples :attack-pattern 2 maximal?)
         judgements (n-examples :judgement 3 maximal?)
         relationships (concat (mk-relationships "indicates" indicators attack-patterns)
                               (mk-relationships "based-on" judgements indicators))]
     {:indicators indicators
      :attack_patterns attack-patterns
      :judgements judgements
      :relationships relationships})))

(defn sightings-threat-ctx-bundle
  "generate n sightings related to a simple indicator threat context"
  ([n] (sightings-threat-ctx-bundle n false))
  ([n maximal?]
   (let [sightings (n-examples :sighting n maximal?)
         threat-context (threat-ctx-bundle)
         indicators (:indicators threat-context)
         sight-indic-rels (mk-relationships "sighting-of" sightings indicators)]
     (-> (assoc threat-context :sightings sightings)
         (update :relationships concat sight-indic-rels)))))

(defn incident-threat-ctx-bundle
  "generate n sightings related to a simple indicator threat context"
  ([nb-sightings] (incident-threat-ctx-bundle nb-sightings false))
  ([nb-sightings maximal?]
   (let [incidents (n-examples :incident 1 maximal?)
         threat-context (sightings-threat-ctx-bundle nb-sightings maximal?)
         sightings (:sightings threat-context)
         sight-incid-rels (mk-relationships "member-of" sightings incidents)]
     (-> (assoc threat-context :incidents incidents)
         (update :relationships concat sight-incid-rels)))))
