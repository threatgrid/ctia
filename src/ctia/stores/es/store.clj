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

;; make it easy to eventually refactor 
;; (do (def ..) .. (defrecord))
;; to
;; (let [...] (reify))
(defmacro let-defs [bs & body]
  (assert (even? (count bs)))
  `(do ~@(map (fn [[k v]] `(def ~k ~v))
              (partition-all 2 bs))
       ~@body))

(defmacro def-es-store [store-name entity-kw stored-schema partial-stored-schema
                        & {:keys [store-opts extra-impls]}]
  (assert (simple-symbol? store-name) (pr-str store-name))
  (let [state-sym 'state]
    ;; gensyms will bloat namespace if reloaded. prefer tools.namespace refresh over reloading.
    `(let-defs [partial-stored-schema# ~partial-stored-schema
                stored-schema# ~stored-schema
                entity-kw# ~entity-kw
                _# (assert (keyword? entity-kw#) (pr-str entity-kw#))
                store-opts# ~store-opts
                slice-opts# #(some-> store-opts# (select-keys %) not-empty list)
                create1-map-arg# (slice-opts# [:stored->es-stored :es-stored->stored :es-stored-schema])
                read1-map-arg# (slice-opts# [:partial-stored-schema :es-partial-stored->partial-stored])
                update1-map-arg# (slice-opts# [:stored-schema :stored->es-stored])
                read-record# (apply crud/handle-read partial-stored-schema# read1-map-arg#)
                read-records# (apply crud/handle-read-many partial-stored-schema# read1-map-arg#)
                create-record# (apply crud/handle-create entity-kw# stored-schema# create1-map-arg#)
                update-record# (apply crud/handle-update entity-kw# stored-schema# update1-map-arg#)
                delete-record# (crud/handle-delete entity-kw#)
                bulk-update# (apply crud/bulk-update stored-schema# update1-map-arg#)
                list-records# (apply crud/handle-find partial-stored-schema# read1-map-arg#)
                query-string-search# (apply crud/handle-query-string-search partial-stored-schema# read1-map-arg#)]
       (defrecord ~store-name [~state-sym]
         IStore
         (store/read-record [_# id# ident# params#]
           (read-record# ~state-sym id# ident# params#))
         (store/read-records [_# ids# ident# params#]
           (read-records# ~state-sym ids# ident# params#))
         (store/create-record [_# new-actors# ident# params#]
           (create-record# ~state-sym new-actors# ident# params#))
         (store/update-record [_# id# actor# ident# params#]
           (update-record# ~state-sym id# actor# ident# params#))
         (store/delete-record [_# id# ident# params#]
           (delete-record# ~state-sym id# ident# params#))
         (store/bulk-delete [_# ids# ident# params#]
           (crud/bulk-delete ~state-sym ids# ident# params#))
         (store/bulk-update [_# docs# ident# params#]
           (bulk-update# ~state-sym docs# ident# params#))
         (store/list-records [_# filter-map# ident# params#]
           (list-records# ~state-sym filter-map# ident# params#))
         (store/close [_#]
           (close-connections! ~state-sym))

         IQueryStringSearchableStore
         (store/query-string-search [_# args#]
           (query-string-search# ~state-sym args#))
         (store/query-string-count [_# search-query# ident#]
           (crud/handle-query-string-count ~state-sym search-query# ident#))
         (store/aggregate [_# search-query# agg-query# ident#]
           (crud/handle-aggregate ~state-sym search-query# agg-query# ident#))
         (store/delete-search [_# search-query# ident# params#]
           (crud/handle-delete-search ~state-sym search-query# ident# params#))

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
