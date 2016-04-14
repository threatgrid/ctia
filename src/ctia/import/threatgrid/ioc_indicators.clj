(ns ctia.import.threatgrid.ioc-indicators
  (:gen-class)
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [clj-time.format :as f]
            [clojure.pprint :refer [pprint]]
            [ctia.domain.conversion :refer [->confidence]]
            [ctia.schemas.indicator :as si]
            [schema.core :as s]))

(s/defschema IoCIndicator
  {(s/required-key "confidence") s/Int
   (s/required-key "author") s/Str
   (s/required-key "tags") [s/Str]
   (s/required-key "name") s/Str
   (s/required-key "created-at") (s/maybe s/Str)
   (s/required-key "variables") [s/Str]
   (s/required-key "title") s/Str
   (s/required-key "last-modified") (s/maybe s/Str)
   (s/required-key "category") [s/Str]
   (s/required-key "severity") s/Int
   (s/required-key "description") (s/maybe s/Str)})

(defn ioc-indicator->ctia-indicator [validate-ioc-fn validate-ctia-fn]
  (fn [{:strs [name title description confidence variables created-at tags category]
        :as ioc-indicator}]
    (validate-ioc-fn ioc-indicator)
    (validate-ctia-fn (-> {:title name
                          :type "indicator"
                          :short_description title
                          :description (str description)
                          :confidence (->confidence confidence)
                          ;; :severity is available but not recorded
                          :tags (vec (set (concat tags category)))
                          :producer "ThreatGrid"
                          :specifications [{:type "ThreatBrain"
                                            :variables variables}]}
                         (cond-> created-at (assoc-in [:valid_time :start_time]
                                                      (f/parse (:basic-date f/formatters)
                                                               created-at)))))))

(defn ioc-indicators->ctia-indicators
  [ioc-indicators & {:keys [validate?]
                     :or {validate? false}}]
  (let [validate-ioc (if validate?
                       (partial s/validate IoCIndicator)
                       identity)
        validate-ind (if validate?
                       (partial s/validate si/NewIndicator)
                       identity)
        transform (ioc-indicator->ctia-indicator validate-ioc validate-ind)]
    (for [ioc-indicator ioc-indicators]
      (transform ioc-indicator))))

(defn load-indicators-from-ioc-file
  [ioc-indicators & {:keys [dry-run? ctia-url api-key]}]
  (doall
   (if dry-run?
     (ioc-indicators->ctia-indicators ioc-indicators :validate? true)
     (for [indicator (ioc-indicators->ctia-indicators ioc-indicators)]
       (http/post (str ctia-url "/ctia/indicator")
                  (-> {:content-type :json
                       :accept :json
                       :throw-exceptions true
                       :socket-timeout 30000
                       :conn-timeout 30000
                       :body (json/generate-string indicator)}
                      (cond-> api-key (assoc :headers {"api_key" api-key}))))))))

(defn -main [& [file-path url api-key :as _args_]]
  (assert (not-empty file-path), "File path must be set")
  (assert (not-empty url), "URL must be set")

  (let [ioc-indicators (-> (slurp file-path) json/parse-string)]
    (println "Checking Indicators...")
    (load-indicators-from-ioc-file ioc-indicators
                                   :dry-run true
                                   :ctia-url url
                                   :api-key (not-empty api-key))
    (println "Loading Indicators...")
    (let [results
          (load-indicators-from-ioc-file ioc-indicators
                                         :dry-run false
                                         :ctia-url url
                                         :api-key (not-empty api-key))]
      (->> results
           (map :body)
           (map json/parse-string)
           (map #(hash-map (get % "title") (get % "id")))
           pprint))))
