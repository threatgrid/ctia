(ns ctia.entity.sighting.es-store
  (:require [ctia.entity.sighting.schemas
             :refer
             [PartialStoredSighting StoredSighting]]
            [ctia.lib.pagination :refer [list-response-schema]]
            [ctia.schemas.core :refer [Observable]]
            [ctia.schemas.search-agg :refer [QueryStringSearchArgs]]
            [ctia.store :refer [IQueryStringSearchableStore ISightingStore IStore] :as store]
            [ctia.stores.es
             [store :refer [close-connections! def-es-store]]
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
                :enabled false}})}})

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

(s/defn add-observables_hash [{{:keys [observables] :as s} :doc}]
  (assoc s :observables_hash (obs->hashes observables)))

(defn remove-observables_hash [{:keys [doc]}]
  (dissoc doc :observables_hash))

(def all-es-store-opts
  {:stored->es-stored add-observables_hash
   :es-stored->stored remove-observables_hash
   :es-partial-stored->partial-stored remove-observables_hash
   :es-stored-schema ESStoredSighting
   :stored-schema StoredSighting
   :partial-stored-schema PartialStoredSighting})

(def-es-store SightingStore :sighting StoredSighting PartialStoredSighting
  :store-opts all-es-store-opts
  :extra-impls
  [ISightingStore
   (list-sightings-by-observables [this observables ident params]
     (store/list-records this
                         {:all-of {:observables_hash
                                   (obs->hashes observables)}}
                         ident
                         params))])
