(ns ctia.stores.es.crud
  (:require [clojure.core.async.impl.protocols :as ap]
            [ctia.lib.es.index :refer [ESConnState]]
            [schema.core :as s]
            [schema.coerce :as c]
            [ring.swagger.coerce :as sc]
            [ctia.lib.pagination :refer [list-response-schema]]
            [ctia.lib.es.document :as d]
            [ctia.schemas.core :refer [ID]]
            [ctia.stores.store-pipe :as sp]
            [ctia.stores.es.create-pipe :as ecp]))

(defn coerce-to-fn
  [Model]
  (c/coercer! Model sc/json-schema-coercion-matcher))

(defn create-doc [entity state mapping]
  (d/create-doc (:conn state)
                (:index state)
                (name mapping)
                entity
                (get-in state [:props :refresh] false)))

(defn handle-create
  "Generate an ES create handler using some mapping and schema"
  [mapping Model]
  (let [coerce! (coerce-to-fn (s/maybe Model))]
    (s/fn :- (s/protocol ap/Channel)
      [state :- ESConnState
       entity-chan :- (s/protocol ap/Channel)]
      (ecp/es-create
       {:conn (:conn state)
        :document-chan entity-chan
        :index (:index state)
        :mapping-type (name mapping)
        :refresh? (get-in state [:props :refresh] false)}))))

(defn update-doc [entity id state mapping]
  (d/update-doc (:conn state)
                (:index state)
                (name mapping)
                id
                entity
                (get-in state [:props :refresh] false)))

(defn handle-update
  "Generate an ES update handler using some mapping and schema"
  [mapping Model]
  (let [coerce! (coerce-to-fn (s/maybe Model))]
    (s/fn :- (s/protocol ap/Channel)
      [state :- ESConnState
       id :- ID
       updated-entity-chan :- (s/protocol ap/Channel)]
      (sp/apply-store-fn
       {:store-fn (s/fn update-fn :- (s/maybe Model)
                    [updated-entity :- Model]
                    (-> (update-doc updated-entity id state mapping)
                        coerce!))
        :input-chan updated-entity-chan}))))

(defn handle-read
  "Generate an ES read handler using some mapping and schema"
  [mapping Model]
  (let [coerce! (coerce-to-fn (s/maybe Model))]
    (s/fn :- (s/maybe Model)
      [state :- ESConnState
       id :- ID]
      (-> (d/get-doc (:conn state)
                     (:index state)
                     (name mapping)
                     id)
          coerce!))))

(defn handle-delete
  "Generate an ES delete handler using some mapping and schema"
  [mapping Model]
  (s/fn :- (s/protocol ap/Channel)
    [state :- ESConnState
     id-chan :- (s/protocol ap/Channel)]
    (sp/apply-store-fn
     {:store-fn (s/fn delete-fn :- s/Bool
                  [id :- ID]
                  (d/delete-doc (:conn state)
                                (:index state)
                                (name mapping)
                                id
                                (get-in state [:props :refresh] false)))
      :input-chan id-chan})))

(defn handle-find
  "Generate an ES find/list handler using some mapping and schema"
  [mapping Model]
  (let [response-schema (list-response-schema Model)
        coerce! (coerce-to-fn response-schema)]
    (s/fn :- response-schema
      [state :- ESConnState
       filter-map :- {s/Any s/Any}
       params]

      (coerce!
       (d/search-docs (:conn state)
                      (:index state)
                      (name mapping)
                      filter-map
                      params)))))
