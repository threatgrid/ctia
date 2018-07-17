(ns ctia.entity.weakness.mapping
  (:require [ctia.stores.es.mapping :as em]))

(def detection-methods
  {:properties
   {:method em/token
    :description em/token
    :effectiveness em/token}})

(def architectures
  {:properties
   {:prevalence em/token
    :name em/token
    :class em/token}})

(def alternate-terms
  {:properties
   {:term em/token
    :description em/token}})

(def languages
  {:properties
   {:prevalence em/token
    :name em/token
    :class em/token}})

(def paradigms
  {:properties
   {:prevalence em/token
    :name em/token}})

(def potential-mitigations
  {:properties {:description em/token
                :phases em/token
                :strategy em/token
                :effectiveness em/token
                :effectiveness_notes em/token}})

(def common-consequences
  {:properties
   {:scopes em/token
    :impacts em/token
    :likelihood em/token
    :note em/token}})

(def operating-systems
  {:properties {:prevalence em/token
                :name em/token
                :version em/token
                :cpe_id em/token
                :class em/token}})

(def notes
  {:properties
   {:type em/token
    :note em/token}})

(def technologies
  {:properties
   {:prevalence em/token
    :name em/token}})

(def modes-of-introduction
  {:properties {:phase em/token
                :note em/token}})

(def weakness-mapping
  {"weakness"
   {:dynamic false
    :include_in_all false
    :properties
    (merge
     em/base-entity-mapping
     em/describable-entity-mapping
     em/sourcable-entity-mapping
     em/stored-entity-mapping
     {:detection_methods detection-methods
      :background_details em/token
      :tlp em/token
      :architectures architectures
      :affected_resources em/token
      :structure em/token
      :abstraction_level em/token
      :alternate_terms alternate-terms
      :languages languages
      :paradigms paradigms
      :portential_mitigations potential-mitigations
      :functional_areas em/token
      :common_consequences common-consequences
      :operating_systems operating-systems
      :likelihood em/token
      :technologies technologies
      :modes_of_introduction modes-of-introduction})}})
