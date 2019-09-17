(ns ctia.stores.es.init
  (:require
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
  [{:keys [entity indexname shards replicas mappings aliased]
    :or {aliased false}
    :as props} :- StoreProperties]
  (let [write-index (str indexname
                         (when aliased "-write"))
        settings {:number_of_shards shards
                  :number_of_replicas replicas}]
    {:index indexname
     :props (assoc props :write-index write-index)
     :config (into
              {:settings (merge store-settings settings)
               :mappings (get store-mappings entity mappings)}
              (when aliased
                {:aliases {indexname {}}}))
     :conn (connect props)}))

(s/defn init-es-conn! :- ESConnState
  "initiate an ES Store connection,
   put the index template, return an ESConnState"
  [properties :- StoreProperties]
  (let [{:keys [conn index props config] :as conn-state}
        (init-store-conn properties)
        existing-index (es-index/get conn (str index "*"))]
    (es-index/create-template! conn index config)
    (when (and (:aliased props)
               (empty? existing-index))
      ;;https://github.com/elastic/elasticsearch/pull/34499
      (es-index/create! conn
                        (format "<%s-{now/d}-000001>" index)
                        (update config :aliases assoc (:write-index props) {})))
    (cond-> conn-state
      (contains? existing-index (keyword index))
      (assoc-in [:props :write-index] index))))


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
