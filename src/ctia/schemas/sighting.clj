(ns ctia.schemas.sighting
  (:require [ctia.lib.time :as time]
            [ctia.schemas.common :as c]
            [ctia.schemas.relationships :as rel]
            [ctia.schemas.vocabularies :as v]
            [schema.core :as s]
            [schema-tools.core :as st]))

(s/defschema Sighting
  "See http://stixproject.github.io/data-model/1.2/indicator/SightingType/"
  ;; Using s/pred break generative testing
  ;; So for now we check the predicate at creation with
  ;; `check-new-sighting`.
  ;; -- (s/pred
  ;; --  ;; We need either an observable or an indicator,
  ;; --  ;; as a Sighting is useless without one of them.
  ;; --  #(not (and (empty? (:observables %))
  ;; --             (empty? (:indicators %)))))
  (st/merge
    {:id c/ID
     :timestamp c/Time
     :description s/Str
     ;; wether this Sighting is intended to be shared, replicated, copied...
     ;; TLPValue is an enum "red", "yellow", "green", "white"  default green.
     :tlp c/TLPValue}
    (st/optional-keys
     {:source s/Str
      ;; if we have a source, we should have a source URI for more details
      :source_uri c/URI
      ;; The openC2 Actuator name that best fits the device that is
      ;; creating this sighting
      :source_device v/Sensor ;; eg. "network.firewall"
      ;; link to some random object
      :reference c/URI
      :confidence v/HighMedLow
      ;; The object(s) of interest.
      :observables [c/Observable]
      ;; the indicators we think we are seeing
      :indicators [rel/RelatedIndicator]
      ;; provide any context we can about where the observable came from.
      ;; `ObservedRelation` should be the current ctia.relations
      ;; namespace, moved into the ctia.schema.common namespace
      :relations [c/ObservedRelation]})))

(s/defschema Type
  (s/enum "sighting"))

(s/defschema NewSighting
  (st/merge
   (st/dissoc Sighting :id)
   {(s/optional-key :type) Type}))

(s/defschema StoredSighting
  "An sighting as stored in the data store"
  (c/stored-schema "sighting" Sighting))

(s/defn realize-sighting :- StoredSighting
  ([new-sighting :- NewSighting
    id :- s/Str
    login :- s/Str]
   (realize-sighting new-sighting id login nil))
  ([new-sighting :- NewSighting
    id :- s/Str
    login :- s/Str
    prev-sighting :- (s/maybe StoredSighting)]
   (let [now (time/now)]
     (assoc new-sighting
            :id id
            :type "sighting"
            :owner login
            :created (or (:created prev-sighting) now)
            :modified now))))

(s/defn check-new-sighting :- s/Bool
  "We need either an observable or an indicator,
   as a Sighting is useless without one of them."
  [sighting :- NewSighting]
  (not (and (empty? (:observables sighting))
            (empty? (:indicators sighting)))))
