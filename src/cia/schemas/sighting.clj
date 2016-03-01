(ns cia.schemas.sighting
  (:require [cia.schemas.common :as c]
            [cia.schemas.relationships :as rel]
            [cia.schemas.vocabularies :as v]
            [schema.core :as s]
            [ring.swagger.schema :refer [describe]]
            [schema-tools.core :as st])
  (:import [java.net URI]))

(s/defschema Sighting
  "See http://stixproject.github.io/data-model/1.2/indicator/SightingType/"
  (merge
   c/GenericStixIdentifiers
   {:source
    (describe [s/Str] #_InformationSourceType "This field provides a name or description of the sighting source.")

    :reference
    (describe [URI] "This field provides a formal reference to the sighting source.")

    :confidence
    (describe [s/Str] #_ConfidenceType "This field provides a confidence assertion in the accuracy of this sighting.")

    :description
    (describe [s/Str] #_StructuredTextType "The Description field is optional and enables an unstructured, text description of this Sighting.")

    :related_observables
    (describe [s/Str] #_RelatedObservablesType "The Related_Observable field identifies or characterizes one or more cyber observables related to this sighting.")
}))

(s/defschema NewSighting
   (st/dissoc Sighting :id))

(s/defschema StoredSighting
  "A Sightings as stored in the data store"
  (st/merge Sighting
            {:owner s/Str
             :created c/Time
             :modified c/Time}))

