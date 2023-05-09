(ns ctia.stores.es.store
  (:require
   [clojure.string :as str]
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

(s/defschema StoreOpts
  {:stored->es-stored (s/pred ifn?)
   :es-stored->stored (s/pred ifn?)
   :es-partial-stored->partial-stored (s/pred ifn?)
   :es-stored-schema (s/protocol s/Schema)
   :es-partial-stored-schema (s/protocol s/Schema)})

(s/defn es-store-impls
  [entity-kw :- s/Keyword
   stored-schema :- (s/protocol s/Schema)
   partial-stored-schema :- (s/protocol s/Schema)
   store-opts :- (s/maybe StoreOpts)]
  (let [slice-opts #(some-> store-opts (select-keys %) list)
        create1-map-arg (slice-opts [:stored->es-stored :es-stored->stored :es-stored-schema])
        read1-map-arg (slice-opts [:es-partial-stored-schema :es-partial-stored->partial-stored])
        update1-map-arg (slice-opts [:es-stored-schema :stored->es-stored])]
    {:read-record (apply crud/handle-read partial-stored-schema read1-map-arg)
     :read-raw-record (crud/handle-read partial-stored-schema)
     :read-records (apply crud/handle-read-many partial-stored-schema read1-map-arg)
     :create-record (apply crud/handle-create entity-kw stored-schema create1-map-arg)
     :update-record (apply crud/handle-update entity-kw stored-schema update1-map-arg)
     :delete-record (crud/handle-delete entity-kw)
     :bulk-update (apply crud/bulk-update stored-schema update1-map-arg)
     :list-records (apply crud/handle-find partial-stored-schema read1-map-arg)
     :query-string-search (apply crud/handle-query-string-search partial-stored-schema read1-map-arg)}))

(defmacro def-es-store [store-name entity-kw stored-schema partial-stored-schema
                        & {:keys [store-opts extra-impls]}]
  (assert (simple-symbol? store-name) (pr-str store-name))
  (let [des-gsym #(symbol (str "__" (str/replace (munge (str `def-es-store)) \. \_) "__" store-name "__" (name %)))
        qsym #(symbol (-> *ns* ns-name name) (name %))
        impls (des-gsym 'impls)
        qimpls (qsym impls)]
    `(do (def ~impls (es-store-impls ~entity-kw ~stored-schema ~partial-stored-schema ~store-opts))
       (defrecord ~store-name [~'state]
         IStore
         (read-record [this# id# ident# params#]
           ((:read-record ~qimpls) (.state this#) id# ident# params#))
         (read-records [this# ids# ident# params#]
           ((:read-records ~qimpls) (.state this#) ids# ident# params#))
         (create-record [this# new-docs# ident# params#]
           ((:create-record ~qimpls) (.state this#) new-docs# ident# params#))
         (update-record [this# args#]
           ((:update-record ~qimpls)
            (assoc args#
                   :conn-state (.state this#)
                   :read-raw-record #((:read-raw-record ~qimpls)
                                      this# (:id args#) (:ident args#) (:es-params args#)))))
         (delete-record [this# id# ident# params#]
           ((:delete-record ~qimpls) (.state this#) id# ident# params#))
         (bulk-delete [this# ids# ident# params#]
           (crud/bulk-delete (.state this#) ids# ident# params#))
         (bulk-update [this# docs# ident# params#]
           ((:bulk-update ~qimpls) (.state this#) docs# ident# params#))
         (list-records [this# filter-map# ident# params#]
           ((:list-records ~qimpls) (.state this#) filter-map# ident# params#))
         (close [this#]
           (close-connections! (.state this#)))

         IQueryStringSearchableStore
         (query-string-search [this# args#]
           ((:query-string-search ~qimpls) (.state this#) args#))
         (query-string-count [this# search-query# ident#]
           (crud/handle-query-string-count (.state this#) search-query# ident#))
         (aggregate [this# search-query# agg-query# ident#]
           (crud/handle-aggregate (.state this#) search-query# agg-query# ident#))
         (delete-search [this# search-query# ident# params#]
           (crud/handle-delete-search (.state this#) search-query# ident# params#))

         IPaginateableStore
         (paginate [this# fetch-page-fn#]
           (store/paginate this# fetch-page-fn# {}))
         (paginate [this# fetch-page-fn# init-page-params#]
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
