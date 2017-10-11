(ns ctia.task.migrate-es-stores
  (:require [clj-momo.lib.es
             [document :as es-doc]
             [index :as es-index]]
            [clojure.tools.logging :as log]
            [ctia
             [init :refer [init-store-service! log-properties]]
             [properties :as p]
             [store :refer [stores]]]))

(def default-batch-size 100)

(def add-groups
  "set a document group to [\"tenzin\"] if unset"
  (map (fn [{:keys [groups]
            :as doc}]
         (if-not (seq groups)
           (assoc doc :groups ["tenzin"])
           doc))))

(def fix-end-time
  "fix end_time to 2535"
  (map
   (fn [{:keys [valid_time]
        :as doc}]
     (if (:end_time valid_time)
       (update-in doc
                  [:valid_time
                   :end_time]
                  #(clojure.string/replace % #"2535" "2525"))
       doc))))

(def available-operations
  "define all migration operations here"
  {:default (map identity)
   :add-groups add-groups
   :fix-end-time fix-end-time})

(defn compose-operations
  "compose operations from a list of keywords"
  [ops]
  (let [operation-keys (map keyword
                            (clojure.string/split ops #","))
        operations (vals (select-keys available-operations
                                      operation-keys))]
    (apply comp
           (:default available-operations)
           operations)))

(defn source-store-map->target-store-map
  "transform a source store map into a target map,
  essentially updating indexname"
  [store suffix]
  (update store :indexname str "_" suffix))

(defn store->map
  "transfrom a store record
   into a properties map for easier manipulation"
  [store]
  {:conn (-> store first :state :conn)
   :indexname (-> store first :state :index)
   :mapping (-> store first :state :props :entity name)
   :settings (-> store first :state :props :settings)})

(defn stores->maps
  "transform store records to maps"
  [stores]
  (into {}
        (map (fn [[sk sr]]
               {sk (store->map sr)}) stores)))

(defn source-store-maps->target-store-maps
  "transform target store records to maps"
  [current-stores suffix]
  (into {}
        (map (fn [[sk sr]]
               {sk (source-store-map->target-store-map sr suffix)})
             current-stores)))

(defn setup
  "iit properties, start CTIA and its store service"
  []
  (log/warn "starting CTIA Stores...")
  (p/init!)
  (log-properties)
  (init-store-service!))

(defn fetch-batch
  "fetch a batch of documents from an es index"
  [{:keys [conn indexname mapping]} batch-size offset]
  (es-doc/search-docs
   conn
   indexname
   mapping
   nil
   {}
   {:offset (or offset 0)
    :limit batch-size}))

(defn store-batch
  "store a batch of documents using a bulk operation"
  [{:keys [conn indexname mapping]} batch]
  (log/warnf "storing %s records" (count batch))
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
    (log/warnf "purging index: %s"
               wildcard)
    (es-index/delete! (:conn target-store)
                      wildcard))
  (log/warnf "creating index template: %s"
             (:indexname target-store))
  (log/warnf "creating index: %s"
             (:indexname target-store))

  (es-index/create! (:conn target-store)
                    (:indexname target-store)
                    (:settings target-store)))

(defn migrate-store
  "migrate a single store"
  [current-store
   target-store
   operations
   batch-size
   confirm?]

  (when confirm?
    (create-target-store target-store))
  (let [store-size (-> (fetch-batch current-store 1 0)
                       :paging
                       :total-hits)]
    (log/warnf "store size: %s records" store-size))
  (loop [offset 0
         migrated-count 0]
    (let [{:keys [data paging]
           :as batch}
          (fetch-batch current-store
                       batch-size
                       offset)
          next (:next paging)
          offset (:offset next)
          migrated (transduce operations conj data)
          migrated-count (+ migrated-count
                            (count migrated))]
      (when (seq migrated)
        (when confirm?
          (store-batch target-store migrated))
        (log/warnf "migrated: %s" migrated-count))
      (if next
        (recur offset migrated-count)
        (log/warnf "finished migrating store: %s"
                   (:indexname current-store))))))

(defn migrate-store-indexes
  "migrate all es store indexes"
  [suffix ops batch-size confirm?]
  (let [current-stores (stores->maps @stores)
        target-stores
        (source-store-maps->target-store-maps current-stores
                                              suffix)
        operations (compose-operations ops)
        batch-size (or batch-size default-batch-size)]

    (log/warnf "migrating stores: %s" (keys current-stores))
    (log/warnf "batch size: %s" batch-size)

    (doseq [[sk sr] current-stores]
      (log/warnf "migrating store: %s" sk)
      (migrate-store sr
                     (sk target-stores)
                     operations
                     batch-size
                     confirm?))))

(defn -main
  "invoke with lein run -m ctia.task.migrate-es-stores <suffix> <operations> <batch-size> <confirm?>"
  [suffix ops batch-size confirm?]

  (assert suffix
          "Please provide an indexname suffix for target store creation")
  (assert ops "Please provide a csv operation list")
  (assert batch-size "Please specify a batch size")

  (log/warn "migrating all ES Stores")
  (setup)
  (migrate-store-indexes suffix
                         ops
                         (read-string batch-size)
                         (boolean (or confirm? false)))
  (log/warn "migration complete")
  (System/exit 0))
