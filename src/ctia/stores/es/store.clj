(ns ctia.stores.es.store
  (:require
   [ctia.store :refer [IPaginateableStore IQueryStringSearchableStore IStore]
    :as store]
   [ctia.stores.es.crud :as crud]
   [ductile.conn :as es-conn]
   [ductile.index :as es-index]
   [ductile.pagination :refer [default-limit]]
   [ductile.schemas :refer [ESConn]]
   [schema.core :as s]))

(defn delete-state-indexes [{:keys [conn index] :as _state}]
  (when conn
    (es-index/delete-template! conn (str index "*"))
    (es-index/delete! conn (str index "*"))))

(s/defn close-connections!
  [{:keys [conn]}]
  (es-conn/close conn))

(defn all-pages-iteration
  "Returns lazy iteration of consecutive calls to `query-fn` with pagination params.

  Resulted data is a sequence of responses of a shape `{:data [,,,] :paging {:next {,,,}}}`"
  ([query-fn] (all-pages-iteration query-fn {:limit default-limit}))
  ([query-fn {:keys [limit]
              :or {limit default-limit}
              :as params}]
   (iteration query-fn
              :somef #(seq (:data %))
              :kf #(when-let [next-params (get-in % [:paging :next])]
                     (into params next-params))
              :initk (assoc params :limit limit))))

(defn create-es-store-fn [{:keys [entity-kw stored-schema partial-stored-schema]}]
  (assert (keyword? entity-kw) (pr-str entity-kw))
  (assert (map? stored-schema) (pr-str stored-schema))
  (assert (map? partial-stored-schema) (pr-str partial-stored-schema))
  (let [read-record# (crud/handle-read partial-stored-schema)
        handle-read-many# (crud/handle-read-many partial-stored-schema)
        handle-create# (crud/handle-create entity-kw stored-schema)
        handle-update# (crud/handle-update entity-kw stored-schema)
        handle-delete# (crud/handle-delete entity-kw)
        bulk-update# (crud/bulk-update stored-schema)
        handle-find# (crud/handle-find partial-stored-schema)
        handle-query-string-search# (crud/handle-query-string-search partial-stored-schema)]
    (fn [state]
      (reify
        IStore
        (store/read-record [_# id# ident# params#]
          (read-record# state id# ident# params#))
        (store/read-records [_# ids# ident# params#]
          (handle-read-many# state ids# ident# params#))
        (store/create-record [_# new-actors# ident# params#]
          (handle-create# state new-actors# ident# params#))
        (store/update-record [_# id# actor# ident# params#]
          (handle-update# state id# actor# ident# params#))
        (store/delete-record [_# id# ident# params#]
          (handle-delete# state id# ident# params#))
        (store/bulk-delete [_# ids# ident# params#]
          (crud/bulk-delete state ids# ident# params#))
        (store/bulk-update [_# docs# ident# params#]
          (bulk-update# state docs# ident# params#))
        (store/list-records [_# filter-map# ident# params#]
          (handle-find# state filter-map# ident# params#))
        (store/close [_#] (close-connections! state))

        IQueryStringSearchableStore
        (store/query-string-search [_# args#]
          (handle-query-string-search# state args#))
        (store/query-string-count [_# search-query# ident#]
          (crud/handle-query-string-count state search-query# ident#))
        (store/aggregate [_# search-query# agg-query# ident#]
          (crud/handle-aggregate state search-query# agg-query# ident#))
        (store/delete-search [_# search-query# ident# params#]
          (crud/handle-delete-search state search-query# ident# params#))

        IPaginateableStore
        (store/paginate [this# fetch-page-fn#]
          (store/paginate this# fetch-page-fn# {}))
        (store/paginate [this# fetch-page-fn# init-page-params#]
          (all-pages-iteration (partial fetch-page-fn# this#) init-page-params#))))))

(defmacro def-es-store [store-name entity-kw stored-schema partial-stored-schema]
  (assert (symbol? store-name) (pr-str store-name))
  (let [state-sym (gensym 'state)
        ctor (symbol (str "->" (name store-name)))]
    `(let [f# (create-es-store-fn {:entity-kw ~entity-kw
                                   :stored-schema ~stored-schema
                                   :partial-stored-schema ~partial-stored-schema})]
       (defn ~ctor [state#]
         (f# state#)))))

(s/defschema StoreMap
  {:conn ESConn
   :indexname s/Str
   :mapping s/Str
   :type s/Str
   :settings s/Any
   :config s/Any
   :props {s/Any s/Any}})

(s/defn store-state->map :- StoreMap
  "transform a store state
   into a properties map for easier manipulation,
   override the cm to use the custom timeout "
  [{:keys [index props conn config]}
   conn-overrides]
  (let [entity-type (-> props :entity name)]
    {:conn (merge conn conn-overrides)
     :indexname index
     :props props
     :mapping entity-type
     :type entity-type
     :settings (:settings props)
     :config config}))

(s/defn store->map :- StoreMap
  "transform a store record
   into a properties map for easier manipulation,
   override the cm to use the custom timeout "
  [store conn-overrides]
  (-> store first :state
      (store-state->map conn-overrides)))
