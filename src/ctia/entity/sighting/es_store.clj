(ns ctia.entity.sighting.es-store
  (:require [clj-momo.lib.es.schemas :refer [ESConnState]]
            [ctia.entity.sighting.schemas
             :refer
             [PartialStoredSighting StoredSighting]]
            [ctia.lib.pagination :refer [list-response-schema]]
            [ctia.schemas.core :refer [Observable]]
            [ctia.store :refer [IQueryStringSearchableStore ISightingStore IStore]]
            [ctia.stores.es
             [crud :as crud]
             [mapping :as em]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(def sighting-mapping
  {"sighting"
   {:dynamic "strict"
    :include_in_all false
    :properties
    (merge
     em/base-entity-mapping
     em/describable-entity-mapping
     em/sourcable-entity-mapping
     em/stored-entity-mapping
     {:observed_time em/valid-time
      :count {:type "long"}
      :sensor em/token
      :targets em/sighting-target
      :reference em/token
      :confidence em/token
      :severity em/token
      :resolution em/token
      :internal {:type "boolean"}
      :observables em/observable
      :observables_hash em/token
      :relations em/observed-relation})}})

(s/defschema ESStoredSighting
  (st/assoc StoredSighting :observables_hash [s/Str]))

(s/defschema ESPartialStoredSighting
  (st/assoc PartialStoredSighting (s/optional-key :observables_hash) [s/Str]))

(def ESStoredSightingList (list-response-schema ESStoredSighting))
(def ESPartialStoredSightingList (list-response-schema ESPartialStoredSighting))

(def StoredSightingList (list-response-schema StoredSighting))
(def PartialStoredSightingList (list-response-schema PartialStoredSighting))

(def es-coerce! (crud/coerce-to-fn [(s/maybe ESPartialStoredSighting)]))

(def create-fn (crud/handle-create :sighting ESStoredSighting))
(def read-fn (crud/handle-read :sighting ESPartialStoredSighting))
(def update-fn (crud/handle-update :sighting ESStoredSighting))
(def list-fn (crud/handle-find :sighting ESPartialStoredSighting))
(def handle-query-string-search (crud/handle-query-string-search :sighting ESPartialStoredSighting))

(s/defn observable->observable-hash :- s/Str
  "transform an observable to a hash of the form type:value"
  [{:keys [type value] :as o} :- Observable]
  (str type ":" value))

(s/defn obs->hashes :- [s/Str]
  "transform a list of observables into observable hashes"
  [observables :- [Observable]]
  (map observable->observable-hash observables))

(s/defn stored-sighting->es-stored-sighting
  :- (s/maybe ESStoredSighting)
  "adds an observables hash to a sighting"
  [{:keys [observables] :as s} :- (s/maybe StoredSighting)]
  (when s
    (assoc s :observables_hash
           (map observable->observable-hash observables))))

(s/defn partial-stored-sighting->es-partial-stored-sighting
  :- (s/maybe ESPartialStoredSighting)
  "adds an observables hash to a partial-sighting"
  [{:keys [observables] :as s} :- (s/maybe PartialStoredSighting)]
  (when s
    (if observables
      (assoc s :observables_hash
             (map observable->observable-hash observables))
      s)))

(s/defn es-stored-sighting->stored-sighting
  :- (s/maybe StoredSighting)
  "remove the computed observables hash from a sighting"
  [s :- (s/maybe ESStoredSighting)]
  (when s (dissoc s :observables_hash)))

(s/defn es-partial-stored-sighting->partial-stored-sighting
  :- (s/maybe PartialStoredSighting)
  "remove the computed observables hash from a sighting"
  [s :- (s/maybe ESPartialStoredSighting)]
  (when s (dissoc s :observables_hash)))

(s/defn handle-create :- [StoredSighting]
  [state :- ESConnState
   new-sightings :- [StoredSighting]
   ident
   params]
  (doall
   (as-> new-sightings $
     (map stored-sighting->es-stored-sighting $)
     (create-fn state $ ident params)
     (map es-stored-sighting->stored-sighting $))))

(s/defn handle-read :- (s/maybe PartialStoredSighting)
  [state id ident params]
  (es-partial-stored-sighting->partial-stored-sighting
   (read-fn state id ident params)))

(s/defn handle-update :- StoredSighting
  [state id realized ident]
  (as-> (stored-sighting->es-stored-sighting realized) $
    (update-fn state id $ ident)
    (es-stored-sighting->stored-sighting $)))

(def handle-delete (crud/handle-delete :sighting StoredSighting))

(s/defn es-paginated-list->paginated-list
  :- PartialStoredSightingList
  [paginated-list :- ESPartialStoredSightingList]
  (update-in paginated-list
             [:data]
             #(map es-partial-stored-sighting->partial-stored-sighting (es-coerce! %))))

(s/defn handle-list :- PartialStoredSightingList
  [state filter-map ident params]
  (es-paginated-list->paginated-list
   (list-fn state filter-map ident params)))

(s/defn handle-query-string-search-sightings
  :- PartialStoredSightingList
  [state query filter-map ident params]
  (es-paginated-list->paginated-list
   (handle-query-string-search state query filter-map ident params)))

(s/defn handle-list-by-observables
  :- PartialStoredSightingList
  [state observables :- [Observable] ident params]
  (handle-list state
               {:observables_hash
                (obs->hashes observables)}
               ident
               params))

(defrecord SightingStore [state]
  IStore
  (read-record [_ id ident params]
    (handle-read state id ident params))
  (create-record [_ new-sightings ident params]
    (handle-create state new-sightings ident params))
  (update-record [_ id sighting ident]
    (handle-update state id sighting ident))
  (delete-record [_ id ident]
    (handle-delete state id ident))
  (list-records [_ filter-map ident params]
    (handle-list state filter-map ident params))
  ISightingStore
  (list-sightings-by-observables [_ observables ident params]
    (handle-list-by-observables state observables ident params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap ident params]
    (handle-query-string-search-sightings state query filtermap ident params)))
