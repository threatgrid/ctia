(ns ctia.task.stats
  (:require [ctia.task.stats.csv :as csv]
            [ctia.store :refer [aggregate]]
            [clojure.set :as set]
            [ctia.stores.es.crud :as crud]
            [clojure.data.json :as json]
            [clojure.string :as string]
            [clojure.pprint :as pp]
            [clj-http.client :as http]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [ctia.init :refer [start-ctia!*]]
            [ctia.store-service :as store-svc]
            [clojure.edn :as edn]
            [ctia.properties :as p]
            [puppetlabs.trapperkeeper.app :as app]))

(defn topn-orgs
  [n]
  {:agg-key :top-orgs
   :agg-type :topn
   :aggregate-on :groups
   :limit n})

(defn topn-sources
  [n]
  {:agg-key :top-sources
   :agg-type :topn
   :aggregate-on :source
   :limit n})

(defn topn-sources-top-orgs
  [nb-sources nb-orgs]
  (let [sources-agg (topn-sources nb-sources)
        orgs-agg (topn-orgs nb-orgs)]
    (assoc sources-agg :aggs orgs-agg)))

(defn topn-orgs-top-sources
  [nb-sources nb-orgs]
  (let [sources-agg (topn-sources nb-sources)
        orgs-agg (topn-orgs nb-orgs)]
    (assoc orgs-agg :aggs sources-agg)))

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


(def admin-ident {:login "johndoe"
                  :groups ["Administators"]})

(defn get-org
  [{:keys [jwt iroh-endpoint org-store]
    :or {iroh-endpoint "https://iroh-adm.int.iroh.site"
         org-store "orgs"}}
   org-id]
  (let [org-url (format "%s/admin/store/crud/%s/%s"
                        iroh-endpoint
                        org-store
                        org-id)
        {:keys [status body] :as res} (http/get org-url
                                        {:accept :edn
                                         :oauth-token jwt
                                         :throw-exceptions false})]
    (when (= 200 status)
      (edn/read-string body))))

(defn enrich-org-bucket
  [iroh-opts
   {org-id :key :as bucket}]
  (assoc bucket
         :org
         (get-org iroh-opts org-id)))

(defn enrich-org-buckets
  [iroh-opts
   buckets]
  (map (partial enrich-org-bucket iroh-opts) buckets))

(defn deep-enrich-top-orgs
  [iroh-opts agg-res]
  (let [enrich (partial enrich-org-buckets iroh-opts)
        change (fn [node]
                 (cond-> node
                   (:top-orgs node) (update :top-orgs enrich)))]
    (clojure.walk/postwalk change agg-res)))

(defn extract-metrics
  [{:keys [get-store] :as store-svc}
   iroh-opts]
  (let [format-fn (partial deep-enrich-top-orgs iroh-opts)
        res (-> (get-store :incident)
                (aggregate
                 {:admin true}
                 (topn-sources 3)
                 admin-ident)
                format-fn)]
    {:data res
     :nb-errors 0}))

(defn start-extracts
  [options]
  (try
    (let [app (let [config (p/build-init-config)]
                (start-ctia!* {:services [store-svc/store-service]
                               :config config}))
          {store-svc :StoreService} (app/service-graph app)
          {:keys [nb-errors] :as res} (extract-metrics store-svc options)]
      (log/info "completed metrics: " res)
      (if (< 0 nb-errors)
        (do
          (log/error "there were errors while rolling over stores")
          (System/exit 1))
        (System/exit 0)))
    (catch Throwable e
      (log/error e "Unknown error")
      (System/exit 2))))

(defn top-orgs-to-csv [top-orgs output-file]
  (->> (:top-orgs top-orgs)
       (map (fn [bucket]
              (update bucket
                      :org
                      #(dissoc % :activation-metas :address))))
       csv/to-csv
       (spit output-file)))

(defn format-raw-es
  [iroh-opts raw-es agg-q]
  (let [{:keys [aggregations]} (json/read-str raw-es  :key-fn keyword)
        formatted (crud/format-agg-result agg-q aggregations)]
    (deep-enrich-top-orgs iroh-opts formatted)))

(def cli-options
  ;; An option with a required argument
  [["-i" "--iroh-endpoint" ""]
   ["-j" "--jwt" "iroh jwt"]
   ["-h" "--help"]])

(defn -main [& args]
  (let [{:keys [options errors summary]} (parse-opts args cli-options)
        {:keys [restart confirm]} options]
    (when errors
      (binding  [*out* *err*]
        (println (string/join "\n" errors))
        (println summary))
      (System/exit 1))
    (when (:help options)
      (println summary)
      (System/exit 0))
    (pp/pprint options)
    (start-extracts options)))
