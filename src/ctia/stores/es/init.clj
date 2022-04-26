(ns ctia.stores.es.init
  (:require
   [clojure.set :as set]
   [clojure.tools.logging :as log]
   [ctia.entity.entities :as entities]
   [ctia.stores.es.mapping :refer [store-settings]]
   [ctia.stores.es.schemas :refer [ESConnServices ESConnState]]
   [ductile.conn :refer [connect]]
   [ductile.document]
   [ductile.index]
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
  (into {} (map (fn [[_ {:keys [entity] :as props}]]
                  [entity (select-keys props [:searchable-fields
                                              :es-mapping])]))
        (entities/all-entities)))

(s/defn init-store-conn :- ESConnState
  "initiate an ES store connection, returning a map containing a
   connection manager and dedicated store index properties"
  [{:keys [entity indexname mappings aliased shards replicas refresh_interval version]
    :or {shards 1
         replicas 1
         refresh_interval "1s"
         version 7}
    :as props} :- StoreProperties
   services :- ESConnServices]
  (let [write-index (str indexname
                         (when aliased "-write"))
        settings {:refresh_interval refresh_interval
                  :number_of_shards shards
                  :number_of_replicas replicas}
        mappings (cond-> (get-in entity-fields [entity :es-mapping] mappings)
                   (< 5 version) (some-> first val))
        searchable-fields (get-in entity-fields [entity :searchable-fields])]
    {:index indexname
     :props (assoc props :write-index write-index)
     :config (cond-> {:settings (into store-settings settings)
                      :mappings mappings}
               aliased (assoc :aliases {indexname {}}))
     :conn (connect props)
     :services services
     :searchable-fields searchable-fields}))

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

(defn get-existing-indices
  [conn index]
  ;; retrieve existing indices using wildcard to identify ambiguous index names
  (let [existing (-> (ductile.index/get conn (str index "*"))
                     keys
                     set)
        index-pattern (re-pattern (str index "(-\\d{4}.\\d{2}.\\d{2}.*)?"))
        matching (into #{} (filter #(re-matches index-pattern (name %)))
                       existing)]
    (when-some [ambiguous (not-empty (set/difference existing matching))]
      (log/warn (format "Ambiguous index names. Index: %s, ambiguous: %s."
                        (pr-str index)
                        (pr-str ambiguous)))
      (system-exit-error))
    existing))

(s/defn init-es-conn! :- ESConnState
  "initiate an ES Store connection,
   put the index template, return an ESConnState"
  [properties :- StoreProperties
   {{:keys [get-in-config]} :ConfigService
    :as services} :- ESConnServices]
  (let [{:keys [conn index props config] :as conn-state} (init-store-conn properties services)
        aliased (:aliased props)]
    (if-some [existing-indices (not-empty (get-existing-indices conn index))]
      (do (if (get-in-config [:ctia :task :ctia.task.update-index-state])
            (update-index-state conn-state)
            (log/info "Not in update-index-state task, skipping update-index-state"))
          (cond-> conn-state
            (and aliased
                 (contains? existing-indices (keyword index)))
            (assoc-in [:props :write-index]
                      (do (log/error "an existing unaliased store was configured as aliased. Switching from unaliased to aliased indices requires a migration."
                                     properties)
                          index))))
      ;else
      (do (upsert-template! conn-state)
          (when aliased
            ;;https://github.com/elastic/elasticsearch/pull/34499
            (ductile.index/create! conn
                                   (format "<%s-{now/d}-000001>" index)
                                   (assoc-in config [:aliases (:write-index props)] {})))
          conn-state))))

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
  (fn _store-factory [store-kw]
    (-> (get-store-properties store-kw get-in-config)
        (init-es-conn! services)
        store-constructor)))

(s/defn ^:private factories [services :- ESConnServices]
  (into {} (map (fn [[_ {:keys [entity es-store]}]]
                  [entity (make-factory es-store services)]))
        (entities/all-entities)))

(s/defn init-store! [services :- ESConnServices
                     store-kw]
  (when-some [factory (get (factories services) store-kw)]
    (factory store-kw)))
