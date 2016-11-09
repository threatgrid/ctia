(ns ctia.stores.es.crud
  (:require [ctia.lib.es.index :refer [ESConnState]]
            [schema.core :as s]
            [schema.coerce :as c]
            [ring.swagger.coerce :as sc]
            [ctia.lib.pagination :refer [list-response-schema]]
            [ctia.lib.es.document :refer [bulk-create-doc
                                          update-doc
                                          get-doc
                                          delete-doc
                                          search-docs]]))

(defn coerce-to-fn
  [Model]
  (c/coercer! Model sc/json-schema-coercion-matcher))

(defn handle-create
  "Generate an ES create handler using some mapping and schema"
  [mapping Model]
  (let [coerce! (coerce-to-fn (s/maybe Model))]
    (s/fn :- (s/maybe [Model])
      [state :- ESConnState
       models :- [Model]]
      (->> (bulk-create-doc (:conn state)
                            (map #(assoc %
                                         :_id (:id %)
                                         :_index (:index state)
                                         :_type (name mapping))
                                 models)
                            (get-in state [:props :refresh] false))
           (map #(dissoc % :_id :_index :_type))
           (map coerce!)))))

(defn handle-update
  "Generate an ES update handler using some mapping and schema"
  [mapping Model]
  (let [coerce! (coerce-to-fn (s/maybe Model))]
    (s/fn :- (s/maybe Model)
      [state :- ESConnState
       id :- s/Str
       realized :- Model]
      (-> (update-doc (:conn state)
                      (:index state)
                      (name mapping)
                      id
                      realized
                      (get-in state [:props :refresh] false))
          coerce!))))

(defn handle-read
  "Generate an ES read handler using some mapping and schema"
  [mapping Model]
  (let [coerce! (coerce-to-fn (s/maybe Model))]
    (s/fn :- (s/maybe Model)
      [state :- ESConnState
       id :- s/Str]
      (-> (get-doc (:conn state)
                   (:index state)
                   (name mapping)
                   id)
          coerce!))))

(defn handle-delete
  "Generate an ES delete handler using some mapping and schema"
  [mapping Model]
  (s/fn :- s/Bool
    [state :- ESConnState
     id :- s/Str]

    (delete-doc (:conn state)
                (:index state)
                (name mapping)
                id
                (get-in state [:props :refresh] false))))

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
       (search-docs (:conn state)
                    (:index state)
                    (name mapping)
                    nil
                    filter-map
                    params)))))

(defn handle-query-string-search
  "Generate an ES query string handler using some mapping and schema"
  [mapping Model]
  (let [response-schema (list-response-schema Model)
        coerce! (coerce-to-fn response-schema)]
    (s/fn :- response-schema
      [state :- ESConnState
       query :- s/Str
       filter-map :- (s/maybe {s/Any s/Any})
       params]

      (coerce!
       (search-docs (:conn state)
                    (:index state)
                    (name mapping)
                    {:query_string {:query query
                                           }}
                    filter-map
                    params)))))
