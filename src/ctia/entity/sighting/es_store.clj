(ns ctia.entity.sighting.es-store
  (:require [ctia.entity.sighting.schemas
             :refer
             [PartialStoredSighting StoredSighting]]
            [ctia.lib.pagination :refer [list-response-schema]]
            [ctia.schemas.core :refer [Observable]]
            [ctia.schemas.search-agg :refer [QueryStringSearchArgs]]
            [ctia.store :refer [IQueryStringSearchableStore ISightingStore IStore] :as store]
            [ctia.stores.es
             [store :refer [close-connections! def-es-store] :as es.store]
             [crud :as crud]
             [mapping :as em]
             [schemas :refer [ESConnState]]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(def sighting-mapping
  {"sighting"
   {:dynamic false
    :properties
    (merge
     em/base-entity-mapping
     em/describable-entity-mapping
     em/sourcable-entity-mapping
     em/stored-entity-mapping
     {:observed_time em/valid-time
      :count em/long-type
      :sensor em/token
      :sensor_coordinates em/sighting-sensor
      :targets em/sighting-target
      :reference em/token
      :confidence em/token
      :severity em/token
      :resolution em/token
      :data em/embedded-data-table
      :internal em/boolean-type
      :observables em/observable
      :observables_hash em/token
      :relations em/observed-relation
      :context {:type "object"
                :enabled false}
      :activity_interval em/valid-time
      :detection_interval em/valid-time
      :modification_interval em/valid-time})}})

(s/defschema ESStoredSighting
  (st/assoc StoredSighting :observables_hash [s/Str]))

(s/defschema ESPartialStoredSighting
  (st/assoc PartialStoredSighting (s/optional-key :observables_hash) [s/Str]))

(def ESPartialStoredSightingList (list-response-schema ESPartialStoredSighting))
(def PartialStoredSightingList (list-response-schema PartialStoredSighting))

(s/defn observable->observable-hash :- s/Str
  "transform an observable to a hash of the form type:value"
  [{:keys [type value] :as _o} :- Observable]
  (str type ":" value))

(s/defn obs->hashes :- [s/Str]
  "transform a list of observables into observable hashes"
  [observables :- [Observable]]
  (map observable->observable-hash observables))

(s/defn stored-sighting->es-stored-sighting
  :- ESStoredSighting
  "adds an observables hash to a sighting"
  [{:keys [observables] :as s} :- StoredSighting]
  (assoc s :observables_hash (map observable->observable-hash observables)))

(s/defn partial-stored-sighting->es-partial-stored-sighting
  :- ESPartialStoredSighting
  "adds an observables hash to a partial-sighting"
  [{:keys [observables] :as s} :- PartialStoredSighting]
  (cond-> s
    observables (assoc :observables_hash (map observable->observable-hash observables))))

(s/defn es-stored-sighting->stored-sighting
  :- StoredSighting
  "remove the computed observables hash from a sighting"
  [s :- ESStoredSighting]
  (dissoc s :observables_hash))

(s/defn es-partial-stored-sighting->partial-stored-sighting
  :- PartialStoredSighting
  "remove the computed observables hash from a sighting"
  [s :- ESPartialStoredSighting]
  (dissoc s :observables_hash))

(s/def store-opts :- es.store/StoreOpts
  {:stored->es-stored (comp stored-sighting->es-stored-sighting :doc)
   :es-stored->stored (comp es-stored-sighting->stored-sighting :doc)
   :es-partial-stored->partial-stored (comp es-partial-stored-sighting->partial-stored-sighting :doc)
   :es-stored-schema ESStoredSighting
   :es-partial-stored-schema ESPartialStoredSighting})

(defn list-sightings-by-observables [this observables ident params]
  (store/list-records this
                      {:all-of {:observables_hash (obs->hashes observables)}}
                      ident
                      params))

(def-es-store SightingStore :sighting StoredSighting PartialStoredSighting
  :store-opts store-opts
  :extra-impls
  [ISightingStore
   (list-sightings-by-observables [this observables ident params]
     (list-sightings-by-observables this
                                    observables
                                    ident
                                    params))])
