(ns ctia.stores.es.init
  (:require
   [clojure.tools.logging :as log]
   [ctia.properties :refer [properties]]
   [ctia.stores.es.mapping :refer [store-settings]]
   [clj-momo.lib.es
    [conn :refer [connect]]
    [index :as es-index]
    [schemas :refer [ESConnState]]]
   [ctia.entity.entities :refer [entities]]
   [schema.core :as s]))

(s/defschema StoreProperties
  {:entity s/Keyword
   :indexname s/Str
   :shards s/Num
   :replicas s/Num
   (s/optional-key :write-suffix) s/Str
   s/Keyword s/Any})

(def store-mappings
  (apply merge {}
         (map (fn [[_ {:keys [entity es-mapping]}]]
                {entity es-mapping})
              entities)))

(s/defn init-store-conn :- ESConnState
  "initiate an ES store connection, returning a map containing a
   connection manager and dedicated store index properties"
  [{:keys [entity indexname mappings aliased shards replicas refresh_interval]
    :or {aliased false
         shards 1
         replicas 1
         refresh_interval "1s"}
    :as props} :- StoreProperties]
  (let [write-index (str indexname
                         (when aliased "-write"))
        settings {:refresh_interval refresh_interval
                  :number_of_shards shards
                  :number_of_replicas replicas}]
    {:index indexname
     :props (assoc props :write-index write-index)
     :config (into
              {:settings (into store-settings settings)
               :mappings (get store-mappings entity mappings)}
              (when aliased
                {:aliases {indexname {}}}))
     :conn (connect props)}))

(s/defn update-settings!
  "read store properties of given stores and update indices settings."
  [{:keys [conn index]
    {:keys [settings]} :config} :- ESConnState]
  (try
    (->> {:index (select-keys settings [:refresh_interval :number_of_replicas])}
         (es-index/update-settings! conn index))
    (log/info "updated settings: " index)
    (catch clojure.lang.ExceptionInfo e
      (log/warn "could not update settings on that store"
                (pr-str (ex-data e))))))

(defn upsert-template!
  [conn index config]
  (es-index/create-template! conn index config)
  (log/infof "updated template: %s" index))

(defn update-mapping!
  [conn index config]
  (try
    (es-index/update-mapping!
     conn
     index
     (:mappings config))
    (catch clojure.lang.ExceptionInfo e
      (log/fatal "cannot update mapping. You probably tried to update the mapping of an existing field. It's only possible to add new field to existing mappings. If you need to modify the type of a field in an existing index, you must perform a migration" (ex-data e))
      (System/exit 1))))

(s/defn init-es-conn! :- ESConnState
  "initiate an ES Store connection,
   put the index template, return an ESConnState"
  [properties :- StoreProperties]
  (let [{:keys [conn index props config] :as conn-state}
        (init-store-conn properties)
        existing-index (es-index/get conn (str index "*"))]
    (when (seq existing-index)
      (update-mapping! conn index config)
      (update-settings! conn-state))
    (upsert-template! conn index config)
    (when (and (:aliased props)
               (empty? existing-index))
      ;;https://github.com/elastic/elasticsearch/pull/34499
      (es-index/create! conn
                        (format "<%s-{now/d}-000001>" index)
                        (update config :aliases assoc (:write-index props) {})))
    (if (and (:aliased props)
             (contains? existing-index (keyword index)))
      (do (log/error "an existing unaliased store was configured as aliased. Switching from unaliased to aliased indices requires a migration."
                     properties)
          (assoc-in conn-state [:props :write-index] index))
      conn-state)))

(s/defn get-store-properties :- StoreProperties
  "Lookup the merged store properties map"
  [store-kw :- s/Keyword]
  (let [props @properties]
    (merge
     {:entity store-kw}
     (get-in props [:ctia :store :es :default] {})
     (get-in props [:ctia :store :es store-kw] {}))))


(defn- make-factory
  "Return a store instance factory. Most of the ES stores are
  initialized in a common way, so this is used to remove boiler-plate
  code."
  [store-constructor]
  (fn store-factory [store-kw]
    (-> (get-store-properties store-kw)
        init-es-conn!
        store-constructor)))

(def ^:private factories
  (apply merge {}
         (map (fn [[_ {:keys [entity es-store]}]]
                {entity (make-factory es-store)})
              entities)))

(defn init-store! [store-kw]
  (when-let [factory (get factories store-kw)]
    (factory store-kw)))
