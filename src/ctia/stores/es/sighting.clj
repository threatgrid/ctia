(ns ctia.stores.es.sighting
  (:require [ctia.stores.es.crud :as crud]
            [ctim.schemas
             [common :refer [Observable]]
             [sighting :refer [StoredSighting]]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(s/defschema ESStoredSighting
  (st/merge StoredSighting
            {:observables_hash [s/Str]}))

(def es-coerce! (crud/coerce-to-fn [(s/maybe ESStoredSighting)]))

(s/defn observable->observable-hash :- s/Str
  [{:keys [type value] :as obsrvable :- Observable}]
  (str (name type) ":" value))

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

(s/defn handle-create-sighting [state realized] :- StoredSighting
  (let [create-fn (crud/handle-create :sighting ESStoredSighting)
        transformed (stored-sighting->es-stored-sighting realized)]
    (-> (create-fn state transformed)
        es-stored-sighting->stored-sighting)))

(s/defn handle-read-sighting [state id] :- StoredSighting
  (let [read-fn (crud/handle-read :sighting ESStoredSighting)]
    (-> (read-fn state id)
        es-stored-sighting->stored-sighting)))

(s/defn handle-update-sighting :- StoredSighting
  [state id realized]
  (let [update-fn (crud/handle-update :sighting ESStoredSighting)
        transformed (stored-sighting->es-stored-sighting realized)]
    (-> (update-fn state id transformed)
        es-stored-sighting->stored-sighting)))

(def handle-delete-sighting (crud/handle-delete :sighting StoredSighting))

(defn es-paginated-list->paginated-list [paginated-list]
  (update-in paginated-list
             [:data]
             #(map es-stored-sighting->stored-sighting (es-coerce! %))))

(defn handle-list-sightings
  [state filter-map params]

  (let [list-fn (crud/handle-find :sighting ESStoredSighting)
        res (list-fn state filter-map params)]

    (es-paginated-list->paginated-list res)))

(def ^{:private true} mapping "sighting")

(defn handle-list-sightings-by-observables
  [state observables params]

  (let [hashes (map #(observable->observable-hash %) observables)]
    (handle-list-sightings state {:observables_hash hashes} params)))
