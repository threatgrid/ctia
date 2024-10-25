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
                       :max_docs 100000000})

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
  [{:keys [index props config] :as store-config}]
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
                  :template base-config}
        ilm-config (into (assoc-in base-config [:aliases write-alias] {:is_write_index true})
                         {:template template
                          :policy policy})]
    (assoc store-config :config ilm-config)))

(s/defn init-store-conn :- ESConnState
  "initiate an ES store connection, returning a map containing a
   connection manager and dedicated store index properties"
  [{:keys [entity indexname mappings shards replicas refresh_interval version]
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
        searchable-fields (get-in entity-fields [entity :searchable-fields])]
    {:index indexname
     :props (assoc props :write-index write-index)
     :config {:settings (into store-settings settings)
              :mappings mappings
              :aliases {indexname {}}}
     :conn (connect props)
     :services services
     :searchable-fields searchable-fields}))

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

(s/defn upsert-index-template!
  [{:keys [conn index config props]} :- ESConnState]
  (when-let [legacy-template (seq (index/get-template conn index))]
    (log/infof "found legacy template for %s Deleting it." index)
    (index/delete-template! conn index))
  (log/info "Creating policy: " index)
  (index/create-policy! conn index (:policy config))
  (log/info "Creating index template: " index)
  (index/create-index-template! conn index (:template config))
  (log/infof "Updated index template: %s" index))

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

(defn ambiguous-indices
  [existing index]
  (let [existing-k (set (keys existing))
        index-pattern (re-pattern (str "((partial|restored)-)?"
                                       index
                                       "(-\\d{4}.\\d{2}.\\d{2}.*)?"))
        matching (filter #(re-matches index-pattern (name %))
                         existing-k)
        ambiguous (difference existing-k (set matching))]
    ambiguous))

(defn get-existing-indices
  [conn index]
  ;; retrieve existing indices using wildcard to identify ambiguous index names
  (let [existing (index/get conn (str index "*"))
        ambiguous (ambiguous-indices existing index)]
    (if (seq ambiguous)
      (do (log/warn (format "Ambiguous index names. Index: %s, ambiguous: %s."
                            (pr-str index)
                            (pr-str ambiguous)))
          (system-exit-error))
      existing)))

(s/defn update-ilm-settings!
  [{:keys [conn index props] :as es-conn} :- ESConnState]
  (upsert-index-template! es-conn)
  (let [existing-indices  (get-existing-indices conn index)
        write-index (:write-index props)
        real-index (filter (fn [[_ {:keys [aliases]}]] (get aliases (keyword write-index))) existing-indices)
        _ (when-not (= 1 (count real-index))
            (throw (ex-info "Cannot update ilm settings, found multiple write indices" real-index)))
        [real-write-indexname real-write-index] (first real-index)
        alias-actions [{:add
                         {:index real-write-indexname
                          :alias write-index
                          :is_write_index true}}]
        policy (mk-policy props)
        lifecycle {:name index :rollover_alias write-index}
        lifecycle-update {:index {:lifecycle lifecycle}}
        update-alias-res (index/alias-actions! conn alias-actions)
        _ (when-not (= {:acknowledged true} update-alias-res)
            (throw (ex-info "Cannot update ilm settings: failed to update write alias." update-alias-res)))
        _ (log/infof "updated write alias: %s" write-index)
        update-settings-res (index/update-settings! conn index lifecycle-update)]
    (when-not (= {:acknowledged true} update-settings-res)
      (throw (ex-info "Cannot update ilm settings." update-settings-res)))
    (log/infof "updated settings with lifecycle" index)
    true))

(defn update-index-state
  ([conn-state] (update-index-state conn-state
                                    {:update-mappings! update-mappings!
                                     :update-settings! update-settings!
                                     :refresh-mappings! refresh-mappings!
                                     :upsert-template! upsert-index-template!
                                     :update-ilm-settings! update-ilm-settings!}))
  ([{{update-mappings?  :update-mappings
      update-settings?  :update-settings
      refresh-mappings? :refresh-mappings
      migrate-to-ilm?   :migrate-to-ilm}
     :props
     :as conn-state}
    {:keys [upsert-template!
            update-mappings!
            update-settings!
            refresh-mappings!
            update-ilm-settings!]}]
    (when migrate-to-ilm?
      (update-ilm-settings! conn-state))
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
  "initiate an ES Store connection, upsert policy, index-template
   create first index if missing, migrate legacy indices. return an ESConnState"
  [properties :- StoreProperties
   {{:keys [get-in-config]} :ConfigService
    :as services} :- ESConnServices]
  (let [{:keys [conn index props config] :as conn-state}
        (-> (init-store-conn properties services)
            mk-index-ilm-config)
        existing-indices (get-existing-indices conn index)]
    (if (seq existing-indices)
      (if (get-in-config [:ctia :task :ctia.task.update-index-state])
        ;; TODO handle update-index-state fn
        (update-index-state conn-state
                            {:update-mappings! update-mappings!
                             :update-settings! update-settings!
                             :refresh-mappings! refresh-mappings!
                             :upsert-template! upsert-index-template!
                             :update-ilm-settings! update-ilm-settings!})
        (log/info "Not in update-index-state task, skipping update-index-state"))
      (upsert-index-template! conn-state))
    (when (empty? existing-indices)
      ;;https://github.com/elastic/elasticsearch/pull/34499
      (index/create! conn
                     (format "<%s-{now/d}-000001>" index)
                     (select-keys config [:mappings :settings :aliases])))
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
