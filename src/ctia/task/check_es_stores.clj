(ns ctia.task.check-es-stores
  (:require
   [ctia.stores.es.store
    :refer [store->map]]
   [clj-momo.lib.es
    [conn :as conn]
    [document :as es-doc]
    [index :as es-index]]
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [ctia
    [store-service :as store-svc]
    [init :refer [start-ctia!*]]
    [properties :as p]]
   [ctia.stores.es-service :as es-svc]
   [ctia.entity.entities :refer [entities]]
   [ctia.entity.sighting.schemas :refer [StoredSighting]]
   [ctia.stores.es.crud :refer [coerce-to-fn]]
   [puppetlabs.trapperkeeper.app :as app]
   [schema-tools.core :as st]
   [schema.core :as s]))

(def default-batch-size 100)
(def timeout (* 5 60000))

(def all-types
  (assoc (into {}
               (map (fn [[_ {:keys [entity stored-schema]}]]
                      {entity stored-schema}) entities))
         :sighting (st/merge StoredSighting
                             {(s/optional-key :observables_hash) s/Any})))

(defn type->schema [entity-type]
  (if-let [schema (get all-types entity-type)]
    schema
    (do (log/warnf "missing schema definition for: %s" type)
        s/Any)))

(defn ^:private setup
  "init properties, start CTIA and its store service.
  returns trapperkeeper app"
  []
  (log/info "starting CTIA Stores...")
  (let [config (p/build-init-config)]
    (start-ctia!* {:services [store-svc/store-service
                              es-svc/es-store-service]
                   :config config})))

(defn fetch-batch
  "fetch a batch of documents from an es index"
  [{:keys [conn
           indexname
           mapping]} batch-size offset sort-keys]
  (let [params
        (merge
         {:offset (or offset 0)
          :limit batch-size}
         (when sort-keys
           {:search_after sort-keys}))]
    (es-doc/search-docs conn indexname mapping nil {} params)))

(defn check-store
  "check a single store"
  [store
   batch-size]
  (let [target-store (store->map
                      store
                      {:cm (conn/make-connection-manager
                            {:timeout timeout})})
        store-schema (type->schema (keyword (:type target-store)))
        coerce! (coerce-to-fn [store-schema])
        store-size (-> (fetch-batch target-store 1 0 nil)
                       :paging
                       :total-hits)]
    (log/infof "%s - store size: %s records"
               (:type target-store)
               store-size)
    (loop [offset 0
           sort-keys nil
           checked-count 0]
      (let [{:keys [data paging]
             :as batch}
            (fetch-batch target-store
                         batch-size
                         offset
                         sort-keys)
            next (:next paging)
            offset (:offset next 0)
            search_after (:sort paging)
            checked-count (+ checked-count
                             (count data))]
        (coerce! data)

        (if next
          (recur offset search_after checked-count)
          (log/infof "%s - finished checking %s documents"
                     (:type target-store)
                     checked-count))))))

(defn check-store-index
  [[sk sr]
   batch-size]
  (try
    (log/infof "checking store: %s" sk)
    (check-store sr batch-size)
    (catch Exception e
      (if-let [errors (some->> (ex-data e) :error (remove nil?))]
        (let [message
              (format (str "The store %s is invalid, certainly a coercion issue "
                           "errors: %s")
                      sk
                      (pr-str errors))]
          (log/error message)
          message)
        (throw e)))))

(defn check-store-indexes
  "check all new es store indexes"
  [app batch-size]
  (let [store-svc (app/get-service app :StoreService)
        current-stores (store-svc/deref-stores store-svc)
        batch-size (or batch-size default-batch-size)]
    (log/infof "checking stores: %s" (keys current-stores))
    (log/infof "set batch size: %s" batch-size)
    (keep #(check-store-index % batch-size) current-stores)))

(defn exit [error?]
  (if error?
    (System/exit -1)
    (System/exit 0)))

(defn ^:private run-check
  [batch-size]
  (assert batch-size "Please specify a batch size")
  (log/info "checking all ES Stores")
  (try
    (let [app (setup)]
      (when-let [errors (seq (check-store-indexes app batch-size))]
        (log/errorf "Schema errors found during check: %s"
                    (pr-str errors))
        (exit true))
      (log/info "check complete")
      (exit false))
    (catch Exception e
      (log/error e "Unexpected error during store checks")
      (exit true))
    (finally
      (log/error "Unknown error")
      (exit true))))

(defn -main
  "invoke with lein run -m ctia.task.check-es-stores <batch-size>"
  [batch-size]
  (let [batch-size (read-string batch-size)]
    (run-check batch-size)))
