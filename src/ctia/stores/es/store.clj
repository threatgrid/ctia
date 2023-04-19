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

(defmacro def-es-store [store-name entity-kw es-stored-schema es-partial-stored-schema
                        & {:keys [stored-schema
                                  partial-stored-schema
                                  es-partial-stored->partial-stored
                                  stored->es-stored
                                  extra-impls]}]
  (assert (simple-symbol? store-name) (pr-str store-name))
  `(let [entity-kw# ~entity-kw
         _# (assert (keyword? entity-kw#) (pr-str entity-kw#))
         es-stored-schema# ~es-stored-schema
         stored-schema# (or ~stored-schema es-stored-schema#)
         es-partial-stored-schema# ~es-partial-stored-schema
         partial-stored-schema# (or ~partial-stored-schema es-partial-stored-schema#)
         es-partial-stored->partial-stored# ~es-partial-stored->partial-stored
         stored->es-stored# (or ~stored->es-stored identity)
         read-record-opts# {:partial-stored-schema partial-stored-schema#
                            :es-partial-stored->partial-stored es-partial-stored->partial-stored#}
         read-record# (crud/handle-read es-partial-stored-schema# read-record-opts#)
         read-records# (crud/handle-read-many es-partial-stored-schema# read-record-opts#)
         create-record# (crud/handle-create entity-kw# es-stored-schema#
                                            {:stored->es-stored stored->es-stored#})
         update-record# (crud/handle-update entity-kw# es-stored-schema#)
         delete-record# (crud/handle-delete entity-kw#)
         bulk-update# (crud/bulk-update es-stored-schema#)
         list-records# (crud/handle-find es-partial-stored-schema#)
         query-string-search# (crud/handle-query-string-search es-partial-stored-schema#)]
     (defn ~(symbol (str "->" (name store-name)))
       [state#]
       (reify
         IStore
         (read-record [_# id# ident# params#]
           (read-record# state# id# ident# params#))
         (read-records [_# ids# ident# params#]
           (read-records# state# ids# ident# params#))
         (create-record [_# new-actors# ident# params#]
           (create-record# state# new-actors# ident# params#))
         (update-record [_# id# actor# ident# params#]
           (update-record# state# id# actor# ident# params#))
         (delete-record [_# id# ident# params#]
           (delete-record# state# id# ident# params#))
         (bulk-delete [_# ids# ident# params#]
           (crud/bulk-delete state# ids# ident# params#))
         (bulk-update [_# docs# ident# params#]
           (bulk-update# state# docs# ident# params#))
         (list-records [_# filter-map# ident# params#]
           (list-records# state# filter-map# ident# params#))
         (close [_#]
           (close-connections! state#))

         IQueryStringSearchableStore
         (query-string-search [_# args#]
           (query-string-search# state# args#))
         (query-string-count [_# search-query# ident#]
           (crud/handle-query-string-count state# search-query# ident#))
         (aggregate [_# search-query# agg-query# ident#]
           (crud/handle-aggregate state# search-query# agg-query# ident#))
         (delete-search [_# search-query# ident# params#]
           (crud/handle-delete-search state# search-query# ident# params#))

         IPaginateableStore
         (store/paginate [this# fetch-page-fn#]
           (store/paginate this# fetch-page-fn# {}))
         (store/paginate [this# fetch-page-fn# init-page-params#]
           (all-pages-iteration (partial fetch-page-fn# this#) init-page-params#))
         ~@extra-impls))))

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
