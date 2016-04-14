(ns ctia.import.threatgrid.ioc-indicators
  (:gen-class)
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [clj-time.format :as f]
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

(defn ioc-indicator->cia-indicator [validate-ioc-fn validate-cia-fn]
  (fn [{:strs [name title description confidence variables created-at tags category]
        :as ioc-indicator}]
    (validate-ioc-fn ioc-indicator)
    (validate-cia-fn (-> {:title name
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

(defn ioc-indicators->cia-indicators
  [ioc-indicators & {:keys [validate?]
                     :or {validate? false}}]
  (let [validate-ioc (if validate?
                       (partial s/validate IoCIndicator)
                       identity)
        validate-ind (if validate?
                       (partial s/validate si/NewIndicator)
                       identity)
        transform (ioc-indicator->cia-indicator validate-ioc validate-ind)]
    (for [ioc-indicator ioc-indicators]
      (transform ioc-indicator))))

(defn load-indicators-from-ioc-file
  [{:keys [dry-run? cia-url api-key]} file]
  (let [ioc-indicators (-> (slurp file) json/parse-string)]
    (if dry-run?
      (doall (ioc-indicators->cia-indicators ioc-indicators :validate? true))
      (doall
       (for [indicator (ioc-indicators->cia-indicators ioc-indicators)]
         (http/post (str cia-url "/cia/indicator")
                    (-> {:content-type :json
                         :accept :json
                         :throw-exceptions false
                         :socket-timeout 30000
                         :conn-timeout 30000
                         :body (json/generate-string indicator)}
                        (cond-> api-key (assoc :headers {"api_key" api-key})))))))))

(comment
  (first
   (loop [[indicator & rest-indicators] (->> (slurp "/Users/stevsloa/Downloads/ioc-definitions-2.json")
                                             json/parse-string)
          validation-failures []]
     (cond
       (nil? indicator)
       validation-failures

       (try (ioc-indicator->cia-indicator indicator)
            true
            (catch Throwable _ false))
       (recur rest-indicators validation-failures)

       :else
       (recur rest-indicators (conj validation-failures indicator)))))

  (def results
    (doall
     (load-indicators-from-ioc-file
      "/Users/stevsloa/Downloads/ioc-definitions-2.json"
      "http://128.107.19.200:3000")))

  (->> results
       (map :body)
       (map json/parse-string)
       (map #(hash-map (get % "title") (get % "id")))
       pr-str
       (spit "/Users/stevsloa/Desktop/ioc_indicator_names.edn")))
