(ns ctia.stores.es.init
  (:require
   [clojure.tools.logging :as log]
   [ctia.properties :as p]
   [clojure.set :refer [difference]]
   [ctia.ductile-service.schemas :refer [RequestFn]]
   [ctia.stores.es.mapping :refer [store-settings]]
   [ctia.stores.es.schemas :refer [ESConnServices ESConnState]]
   [ductile
    [conn :refer [connect]]
    [index]]
   [ctia.entity.entities :as entities]
   [schema.core :as s]
   [schema-tools.core :as st]))

(s/defschema StoreProperties
  (st/merge
    {:entity s/Keyword
     :indexname s/Str
     s/Keyword s/Any}
    (st/optional-keys
      {:shards s/Num
       :replicas s/Num
       :write-suffix s/Str
       :refresh_interval s/Str
       :aliased s/Any
       :request-fn RequestFn})))

;; TODO def => defn
(def store-mappings
  (apply merge {}
         (map (fn [[_ {:keys [entity es-mapping]}]]
                {entity es-mapping})
              (entities/all-entities))))

(s/defn init-store-conn :- ESConnState
  "initiate an ES store connection, returning a map containing a
   connection manager and dedicated store index properties"
  [{:keys [entity indexname mappings aliased shards replicas refresh_interval version]
    :or {aliased false
         shards 1
         replicas 1
         refresh_interval "1s"
         version 7}
    :as props} :- StoreProperties
   {{:keys [request-fn]} :DuctileService
    :as services} :- ESConnServices]
  (let [props (update props :request-fn #(or % request-fn))
        write-index (str indexname
                         (when aliased "-write"))
        settings {:refresh_interval refresh_interval
                  :number_of_shards shards
                  :number_of_replicas replicas}
        mappings (cond-> (get store-mappings entity mappings)
                   (> version 5) (some-> first val))]
    {:index indexname
     :props (assoc props :write-index write-index)
     :config (into
              {:settings (into store-settings settings)
               :mappings mappings}
              (when aliased
                {:aliases {indexname {}}}))
     :conn (connect props)
     :services services}))

(s/defn update-settings!
  "read store properties of given stores and update indices settings."
  [{:keys [conn index]
    {:keys [settings]} :config} :- ESConnState]
  (try
    (->> {:index (select-keys settings [:refresh_interval :number_of_replicas])}
         (ductile.index/update-settings! conn index))
    (log/info "updated settings: " index)
    (catch clojure.lang.ExceptionInfo e
      (log/warn "could not update settings on that store"
                (pr-str (ex-data e))))))

(s/defn upsert-template!
  [{:keys [conn index config]} :- ESConnState]
  (ductile.index/create-template! conn index config)
  (log/infof "updated template: %s" index))

(defn system-exit-error
  []
  (log/error (str "IGNORE THIS LOG UNTIL MIGRATION -- "
                  "CTIA tried to start with an invalid configuration: \n"
                  "- invalid mapping\n"
                  "- ambiguous index names"))
  (System/exit 1))

(s/defn update-mappings!
  [{:keys [conn index]
    {:keys [mappings]} :config} :- ESConnState]
  (let [[entity-type type-mappings] (when (= (:version conn) 5)
                                      (first mappings))
        update-body (or type-mappings mappings)]
    (try
      (log/info "updating mapping: " index)
      (ductile.index/update-mappings! conn
                                      index
                                      entity-type
                                      update-body)
      (catch clojure.lang.ExceptionInfo e
        (log/error "cannot update mapping. You probably tried to update the mapping of an existing field. It's only possible to add new field to existing mappings. If you need to modify the type of a field in an existing index, you must perform a migration"
                   (assoc (ex-data e)
                          :conn conn
                          :mappings mappings))
        (system-exit-error)))))

(defn get-existing-indices
  [conn index]
  ;; retrieve existing indices using wildcard to identify ambiguous index names
  (let [existing (-> (ductile.index/get conn (str index "*"))
                     keys
                     set)
        index-pattern (re-pattern (str index "(-\\d{4}.\\d{2}.\\d{2}.*)?"))
        matching (filter #(re-matches index-pattern (name %))
                         existing)
        ambiguous (difference existing (set matching))]
    (if (seq ambiguous)
      (do (log/warn (format "Ambiguous index names. Index: %s, ambiguous: %s."
                            (pr-str index)
                            (pr-str ambiguous)))
          (system-exit-error))
      existing)))

(defn update-index-state
  [{{update-mappings? :update-mappings
     update-settings? :update-settings}
    :props
    :as conn-state}]
  (when update-mappings?
    (update-mappings! conn-state)
    ;; template update must be after update-mapping
    ;; if it fails a System/exit is triggered because
    ;; this means that the mapping in invalid and thus
    ;; must not be propagated to the template that would accept it
    (upsert-template! conn-state))
  (when update-settings?
    (update-settings! conn-state)))

(s/defn init-es-conn! :- ESConnState
  "initiate an ES Store connection,
   put the index template, return an ESConnState"
  [properties :- StoreProperties
   services :- ESConnServices]
  (let [{:keys [conn index props config] :as conn-state}
        (init-store-conn properties services)
        existing-indices (get-existing-indices conn index)]
    (if (seq existing-indices)
      (update-index-state conn-state)
      (upsert-template! conn-state))
    (when (and (:aliased props)
               (empty? existing-indices))
      ;;https://github.com/elastic/elasticsearch/pull/34499
      (ductile.index/create! conn
                             (format "<%s-{now/d}-000001>" index)
                             (update config :aliases assoc (:write-index props) {})))
    (if (and (:aliased props)
             (contains? existing-indices (keyword index)))
      (do (log/error "an existing unaliased store was configured as aliased. Switching from unaliased to aliased indices requires a migration."
                     properties)
          (assoc-in conn-state [:props :write-index] index))
      conn-state)))

(s/defn get-store-properties :- StoreProperties
  "Lookup the merged store properties map"
  [store-kw :- s/Keyword
   get-in-config]
  (merge
    {:entity store-kw}
    (get-in-config [:ctia :store :es :default] {})
    (get-in-config [:ctia :store :es store-kw] {})))

(s/defn ^:private make-factory
  "Return a store instance factory. Most of the ES stores are
  initialized in a common way, so this is used to remove boiler-plate
  code."
  [store-constructor
   {{:keys [get-in-config]} :ConfigService
    :as services} :- ESConnServices]
  (fn store-factory [store-kw]
    (-> (get-store-properties store-kw get-in-config)
        (init-es-conn! services)
        store-constructor)))

(s/defn ^:private factories [services :- ESConnServices]
  (apply merge {}
         (map (fn [[_ {:keys [entity es-store]}]]
                {entity (make-factory es-store services)})
              (entities/all-entities))))

(s/defn init-store! [store-kw services :- ESConnServices]
  (when-let [factory (get (factories services) store-kw)]
    (factory store-kw)))
