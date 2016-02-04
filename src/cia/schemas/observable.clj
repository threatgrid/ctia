(ns cia.schemas.observable
  (:require [cia.schemas.common :as c]
            [cia.schemas.vocabularies :as v]
            [schema.core :as s]))

;; TODO - Reconcile our observable model
;; with the STIX observable (which is complex)
;; See http://stixproject.github.io/data-model/1.2/cybox/ObservableType/

(s/defschema Observable
  "A simple, atomic value which has a consistent identity, and is
  stable enough to be attributed an intent or nature.  This is the
  classic 'indicator' which might appear in a data feed of bad IPs, or
  bad Domains."
  {:value s/Str
   :type v/ObservableType})
