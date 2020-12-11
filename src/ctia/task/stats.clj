(ns ctia.task.stats
  (:require [ctia.stores :refer [aggregate]]
            [clojure.tools.logging :as log]
            [ctia.init :refer [start-ctia!*]]
            [ctia.store-service :as store-svc]
            [ctia.properties :as p]
            [schema.core :as s]
            [puppetlabs.trapperkeeper.app :as app]))

(defn topn-orgs
  [n]
  {:agg-key :top-orgs
   :agg-type :topn
   :aggregate-on :groups
   :limit n})

(defn topn-sources
  [n]
  {:agg-key :topn-sources
   :agg-type :topn
   :aggregate-on :sources
   :limit n})

(defn topn-sources-top-orgs
  [nb-sources nb-orgs]
  (let [sources-agg (topn-sources nb-sources)
        orgs-agg (topn-orgs nb-orgs)]
    (assoc sources-agg :aggs orgs-agg)))

(def nb-orgs
  {:agg-type :cardinality
   :agg-key :nb-orgs
   :aggregate-on "groups"})

(def histogram-agg
  {:agg-type :histogram
   :agg-key :by-month
   :aggregate-on "created"
   :granularity :month})

(defn topn-sources-per-nb-orgs-per-month-per-source
  [nb-sources]
  (let [sources-agg (topn-sources nb-sources)
        hist-agg (assoc histogram-agg
                        :aggs
                        nb-orgs)]
    (assoc sources-agg
           :aggs
           hist-agg)))

(defn org-name
  [org-id]
  ;; TODO
  nil)

(defn extract-metrics
  [{store-svc}]
  )

(defn -main [& _args]
  (try
    (let [app (let [config (p/build-init-config)]
                (start-ctia!* {:services [store-svc/store-service]
                               :config config}))
          {store-svc :StoreService} (app/service-graph app)
          {:keys [nb-errors] :as res} (extract-metrics store-svc)]
      (log/info "completed metrics: " res)
      (if (< 0 nb-errors)
        (do
          (log/error "there were errors while rolling over stores")
          (System/exit 1))
        (System/exit 0)))
    (catch Throwable e
      (log/error e "Unknown error")
      (System/exit 2))))
