(ns ctia.stores.es.init
  (:require
   [clojure.set :refer [difference]]
   [clojure.tools.logging :as log]
   [ctia.entity.entities :as entities]
   [ctia.stores.es.mapping :refer [store-settings]]
   [ctia.stores.es.schemas :refer [ESConnServices ESConnState]]
   [ductile.conn :refer [connect]]
   [ductile.document :as document]
   [ductile.index :as index]
   [schema-tools.core :as st]
   [schema.core :as s]))

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
     :aliased s/Any})))

(def entity-fields
  (->> (entities/all-entities)
       (map (fn [[_ {:keys [entity] :as props}]]
              (hash-map
               entity (select-keys props [:searchable-fields
                                          :es-mapping]))))
       (apply merge)))

(def default-rollover {:max_size "30gb"
                       :max_docs 100000000
                       :max_age "1y"})

;; https://www.elastic.co/guide/en/elasticsearch/reference/7.10/getting-started-index-lifecycle-management.html#manage-time-series-data-without-data-streams
(defn mk-policy
  [store-props]
  ;; https://www.elastic.co/guide/en/elasticsearch/reference/7.10//ilm-rollover.html
  (let [rollover (:rollover store-props default-rollover)]
    {:phases
     {:hot
      {:actions
       {:rollover rollover}}}}))

(s/defn mk-index-ilm-config
  [{:keys [index props config] :as _store-config}]
  (let [{:keys [mappings settings]} config
        write-alias (:write-index props)
        policy (mk-policy props)
        lifecycle {:name index
                   :rollover_alias write-alias}
        settings-ilm (assoc-in settings [:index :lifecycle] lifecycle)
        base-config {:settings settings-ilm
                     :mappings mappings
                     :aliases {index {}}}
        template {:index_patterns (str index "*")
                  :template base-config}]
    (into (assoc-in base-config [:aliases write-alias] {:is_write_index true})
          {:template template
           :policy policy})))

(s/defn init-store-conn :- ESConnState
  "initiate an ES store connection, returning a map containing a
   connection manager and dedicated store index properties"
  [{:keys [entity indexname mappings
           shards replicas refresh_interval
           version ilm?]
    :or {shards 1
         replicas 1
         refresh_interval "1s"
         version 7}
    :as props} :- StoreProperties
   services :- ESConnServices]
  (let [write-index (str indexname "-write")
        settings {:refresh_interval refresh_interval
                  :number_of_shards shards
                  :number_of_replicas replicas}
        mappings (some-> (get-in entity-fields [entity :es-mapping] mappings)
                         first
                         val)
        searchable-fields (get-in entity-fields [entity :searchable-fields])
        store-config {:index indexname
                      :props (assoc props :write-index write-index)
                      :config {:settings (into store-settings settings)
                               :mappings mappings
                               :aliases {indexname {}}}
                      :conn (connect props)
                      :services services
                      :searchable-fields searchable-fields}]
    (cond-> store-config
      ilm? mk-index-ilm-config)))

(s/defn update-settings!
  "read store properties of given stores and update indices settings."
  [{:keys [conn index]
    {:keys [settings]} :config} :- ESConnState]
  (try
    (->> {:index (select-keys settings [:refresh_interval :number_of_replicas])}
         (index/update-settings! conn index))
    (log/info "updated settings: " index)
    (catch clojure.lang.ExceptionInfo e
      (log/warn "could not update settings on that store"
                (pr-str (ex-data e))))))

(s/defn upsert-template!
  [{:keys [conn index config]} :- ESConnState]
  (index/create-template! conn index config)
  (log/infof "updated template: %s" index))

(defn system-exit-error
  []
  (log/error (str "CTIA tried to start with an invalid configuration: \n"
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
      (index/update-mappings! conn
                              index
                              entity-type
                              update-body)
      (catch clojure.lang.ExceptionInfo e
        (log/error "cannot update mapping. You probably tried to update the mapping of an existing field. It's only possible to add new field to existing mappings. If you need to modify the type of a field in an existing index, you must perform a migration"
                   (assoc (ex-data e)
                          :conn conn
                          :mappings mappings))
        (system-exit-error)))))

(s/defn refresh-mappings!
  [{:keys [conn index]
    {:keys [mappings]} :config} :- ESConnState]
  (try
    (log/info "refreshing mapping: " index)
    (document/update-by-query conn
                              [index] {}
                              {:refresh "true"
                               :conflicts "proceed"
                               :wait_for_completion false})
    (catch clojure.lang.ExceptionInfo e
      (log/error "Cannot refresh mapping."
                 (assoc (ex-data e)
                        :conn conn
                        :mappings mappings))
      (system-exit-error))))

(defn get-existing-indices
  [conn index]
  ;; retrieve existing indices using wildcard to identify ambiguous index names
  (let [existing (-> (index/get conn (str index "*"))
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
  ([conn-state] (update-index-state conn-state
                                    {:update-mappings! update-mappings!
                                     :update-settings! update-settings!
                                     :refresh-mappings! refresh-mappings!
                                     :upsert-template! upsert-template!}))
  ([{{update-mappings?  :update-mappings
      update-settings?  :update-settings
      refresh-mappings? :refresh-mappings}
     :props
     :as conn-state}
    {:keys [upsert-template!
            update-mappings!
            update-settings!
            refresh-mappings!]}]
   (when update-mappings?
     (update-mappings! conn-state)
     ;; template update must be after update-mapping
     ;; if it fails a System/exit is triggered because
     ;; this means that the mapping in invalid and thus
     ;; must not be propagated to the template that would accept it
     (upsert-template! conn-state)
     (when refresh-mappings?
       (refresh-mappings! conn-state)))
   (when update-settings?
     (update-settings! conn-state))))

(s/defn init-es-conn! :- ESConnState
  "initiate an ES Store connection,
   put the index template, return an ESConnState"
  [properties :- StoreProperties
   {{:keys [get-in-config]} :ConfigService
    :as services} :- ESConnServices]
  (let [{:keys [conn index props config] :as conn-state}
        (init-store-conn properties services)
        existing-indices (get-existing-indices conn index)]
    (if (seq existing-indices)
      (if (get-in-config [:ctia :task :ctia.task.update-index-state])
        (update-index-state conn-state)
        (log/info "Not in update-index-state task, skipping update-index-state"))
      (upsert-template! conn-state))
    (when (empty? existing-indices)
      ;;https://github.com/elastic/elasticsearch/pull/34499
      (index/create! conn
                     (format "<%s-{now/d}-000001>" index)
                     (update config :aliases assoc (:write-index props) {})))
      conn-state))

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
        (store-constructor))))

(s/defn ^:private factories [services :- ESConnServices]
  (apply merge {}
         (map (fn [[_ {:keys [entity es-store]}]]
                {entity (make-factory es-store services)})
              (entities/all-entities))))

(s/defn init-store! [services :- ESConnServices
                     store-kw]
  (when-let [factory (get (factories services) store-kw)]
    (factory store-kw)))
