(ns ctia.stores.es.sighting
  (:require [clj-momo.lib.es.schemas :refer [ESConnState]]
            [ctia.lib.pagination :refer [list-response-schema]]
            [ctia.schemas.core :refer [Observable StoredSighting PartialStoredSighting]]
            [ctia.stores.es.crud :as crud]
            [schema-tools.core :as st]
            [schema.core :as s]))

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
