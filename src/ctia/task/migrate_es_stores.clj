(ns ctia.task.migrate-es-stores
  (:require [clj-momo.lib.es
             [document :as es-doc]
             [index :as es-index]]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [ctia
             [init :refer [init-store-service! log-properties]]
             [properties :as p]
             [store :refer [stores]]]
            [ctia.task.migrations :refer [available-migrations]]))

(def default-batch-size 100)

(defn compose-migrations
  "compose migrations from a list of keywords"
  [migration-keys]
  (let [migrations
        (vals (select-keys available-migrations
                           migration-keys))]
    (if (seq migrations)
      (apply comp migrations)
      (do (log/warn "target migration not found, copying data")
          (map identity)))))

(defn prefixed-index [index prefix]
  (let [version-trimmed (string/replace index #"^v\w+.\w+.\w_" "")]
    (str "v" prefix "_" version-trimmed)))

(defn source-store-map->target-store-map
  "transform a source store map into a target map,
  essentially updating indexname"
  [store prefix]
  (update store :indexname #(prefixed-index % prefix)))

(defn store->map
  "transform a store record
   into a properties map for easier manipulation"
  [store]
  (let [store-state (-> store first :state)
        entity-type (-> store-state :props :entity name)]
    {:conn (:conn store-state)
     :indexname (:index store-state)
     :mapping entity-type
     :type entity-type
     :settings (-> store-state :props :settings)
     :config (:config store-state)}))

(defn stores->maps
  "transform store records to maps"
  [stores]
  (into {}
        (map (fn [[sk sr]]
               {sk (store->map sr)}) stores)))

(defn source-store-maps->target-store-maps
  "transform target store records to maps"
  [current-stores prefix]
  (into {}
        (map (fn [[sk sr]]
               {sk (source-store-map->target-store-map sr prefix)})
             current-stores)))

(defn setup
  "init properties, start CTIA and its store service"
  []
  (log/info "starting CTIA Stores...")
  (p/init!)
  (log-properties)
  (init-store-service!))

(defn fetch-batch
  "fetch a batch of documents from an es index"
  [{:keys [conn indexname mapping]} batch-size offset]
  (let [params {:offset (or offset 0)
                :limit batch-size}]
    (es-doc/search-docs
     conn
     indexname
     mapping
     nil
     {}
     params)))

(defn store-batch
  "store a batch of documents using a bulk operation"
  [{:keys [conn indexname mapping type]} batch]
  (log/infof "%s - storing %s records"
             type
             (count batch))

  (let [prepared-docs
        (map #(assoc %
                     :_id (:id %)
                     :_index indexname
                     :_type mapping)
             batch)]
    (es-doc/bulk-create-doc
     conn
     prepared-docs
     false)))

(defn create-target-store
  "create the target store, pushing its template"
  [target-store]
  (let [wildcard (str (:indexname target-store) "*")]
    (log/infof "%s - purging indexes: %s"
               (:type target-store)
               wildcard)
    (es-index/delete!
     (:conn target-store)
     wildcard))
  (log/infof "%s - creating index template: %s"
             (:type target-store)
             (:indexname target-store))
  (log/infof "%s - creating index: %s"
             (:type target-store)
             (:indexname target-store))

  (es-index/create-template!
   (:conn target-store)
   (:indexname target-store)
   (:config target-store))

  (es-index/create!
   (:conn target-store)
   (:indexname target-store)
   (:settings target-store)))

(defn migrate-store
  "migrate a single store"
  [current-store
   target-store
   migrations
   batch-size
   confirm?]
  (when confirm?
    (create-target-store target-store))
  (let [store-size (-> (fetch-batch current-store 1 0)
                       :paging
                       :total-hits)]
    (log/infof "%s - store size: %s records"
               (:type current-store)
               store-size))

  (loop [offset 0
         migrated-count 0]
    (let [{:keys [data paging]
           :as batch}
          (fetch-batch current-store
                       batch-size
                       offset)
          next (:next paging)
          offset (:offset next)
          migrated (transduce migrations conj data)
          migrated-count (+ migrated-count
                            (count migrated))]
      (when (seq migrated)
        (when confirm?
          (store-batch target-store migrated))

        (log/infof "%s - migrated: %s documents"
                   (:type current-store)
                   migrated-count))
      (if next
        (recur offset migrated-count)
        (log/infof "%s - finished migrating %s documents"
                   (:type current-store)
                   migrated-count)))))

(defn migrate-store-indexes
  "migrate all es store indexes"
  [prefix migrations batch-size confirm?]
  (let [current-stores (stores->maps @stores)
        target-stores
        (source-store-maps->target-store-maps current-stores
                                              prefix)
        migrations (compose-migrations migrations)
        batch-size (or batch-size default-batch-size)]

    (log/infof "migrating stores: %s" (keys current-stores))
    (log/infof "set batch size: %s" batch-size)

    (doseq [[sk sr] current-stores]
      (log/infof "migrating store: %s" sk)
      (migrate-store sr
                     (sk target-stores)
                     migrations
                     batch-size
                     confirm?))))

(defn -main
  "invoke with lein run -m ctia.task.migrate-es-stores <prefix> <migrations> <batch-size> <confirm?>"
  [prefix migrations batch-size confirm?]

  (assert prefix "Please provide an indexname prefix for target store creation")
  (assert migrations "Please provide a csv migration list argument")
  (assert batch-size "Please specify a batch size")
  (log/info "migrating all ES Stores")
  (setup)
  (migrate-store-indexes prefix
                         (map keyword (string/split migrations #","))
                         (read-string batch-size)
                         (boolean (or confirm? false)))
  (log/info "migration complete")
  (System/exit 0))
