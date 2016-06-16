(ns ctia.stores.es.sighting
  (:require [ctia.lib.pagination :refer [list-response-schema]]
            [ctia.stores.es.crud :as crud]
            [ctim.schemas
             [common :refer [Observable]]
             [sighting :refer [StoredSighting]]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(s/defschema ESStoredSighting
  (st/assoc StoredSighting :observables_hash [s/Str]))

(def ESStoredSightingList (list-response-schema ESStoredSighting))
(def StoredSightingList (list-response-schema StoredSighting))
(def es-coerce! (crud/coerce-to-fn [(s/maybe ESStoredSighting)]))
(def create-fn (crud/handle-create :sighting ESStoredSighting))
(def read-fn (crud/handle-read :sighting ESStoredSighting))
(def update-fn (crud/handle-update :sighting ESStoredSighting))
(def list-fn (crud/handle-find :sighting ESStoredSighting))

(s/defn observable->observable-hash :- s/Str
  "transform an observable to a hash"
  [{:keys [type value] :as o :- Observable}]
  (str type ":" value))

(s/defn obs->hashes :- [s/Str]
  "transform a list of observables into hashes"
  [observables :- [Observable]]
  (map #(observable->observable-hash %) observables))

(s/defn stored-sighting->es-stored-sighting :- (s/maybe ESStoredSighting)
  "adds an observables hash to a sighting"
  [{:keys [observables] :as s :- (s/maybe StoredSighting)}]
  (when s
    (assoc s :observables_hash
           (map #(observable->observable-hash %) observables))))

(s/defn es-stored-sighting->stored-sighting :- (s/maybe StoredSighting)
  "remove the computed observables hash from a sighting"
  [s :- (s/maybe ESStoredSighting)]
  (when s (dissoc s :observables_hash)))

(s/defn handle-create-sighting :- StoredSighting
  [state realized]
  (->> (stored-sighting->es-stored-sighting realized)
       (create-fn state)
       es-stored-sighting->stored-sighting))

(s/defn handle-read-sighting :- (s/maybe StoredSighting)
  [state id]
  (es-stored-sighting->stored-sighting
   (read-fn state id)))

(s/defn handle-update-sighting :- StoredSighting
  [state id realized]
  (->> (stored-sighting->es-stored-sighting realized)
       (update-fn state id)
       es-stored-sighting->stored-sighting))

(def handle-delete-sighting (crud/handle-delete :sighting StoredSighting))

(s/defn es-paginated-list->paginated-list :- StoredSightingList
  [paginated-list :- ESStoredSightingList]
  (update-in paginated-list
             [:data]
             #(map es-stored-sighting->stored-sighting (es-coerce! %))))

(s/defn handle-list-sightings :- StoredSightingList
  [state filter-map params]
  (es-paginated-list->paginated-list
   (list-fn state filter-map params)))

(s/defn handle-list-sightings-by-observables :- StoredSightingList
  [state observables :- [Observable] params]
  (handle-list-sightings state
                         {:observables_hash
                          (obs->hashes observables)}
                         params))
