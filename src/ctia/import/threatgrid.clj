(ns ctia.import.threatgrid
  (:gen-class)
  (:import [javax.xml.bind DatatypeConverter])
  (:require [cheshire.core :as json]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [clj-time.format :as f]
            [clj-http.client :as http]
            [clojure.edn :as edn]
            [clojure.tools.cli :refer [parse-opts]]
            [ctia.domain.conversion :refer [->confidence]]
            [ctia.import.schemas.external.ioc-indicators :as sei]
            [ctia.schemas.indicator :as si]))

(defn feed-judgements
  "Extract Judgement objects from a list of TG feed entries"
  [feed-entries observable-type observable-field & {:keys [confidence source
                                                           priority
                                                           severity
                                                           days-valid
                                                           disposition]
                                                    :or {confidence "High"
                                                         priority 90
                                                         severity 100
                                                         source "threatgrid-feed"
                                                         days-valid 30
                                                         disposition 2}}]
  (map
   (fn [entry]
     (let [formatter (f/formatters :date-time-no-ms)
           start (clj-time.coerce/from-date
                  (.getTime (DatatypeConverter/parseDateTime (:timestamp entry))))]
       {:observable {:type  observable-type
                     :value (get entry observable-field)}
        :disposition disposition
        :valid_time {:start_time (f/unparse formatter start)
                     :end_time (f/unparse formatter (t/plus start
                                                            (t/days days-valid)))}
        :confidence confidence
        :severity severity
        :source source
        :priority priority
        :source_uri (:info entry)
        :reason (:description entry)
        :reason_uri (:sample entry)
        }))
   feed-entries))

(defn load-judgements-from-feed-file [file ctia-url observable-type observable-field & {:as options}]
  (let [entries (json/parse-string (slurp file) true)
        judgements (apply feed-judgements entries
                          observable-type observable-field options)
        target-url (str ctia-url "/ctia/judgement")]
    (map (fn [judgement]
           (let [options {:content-type :edn
                          :accept :edn
                          :throw-exceptions false
                          :socket-timeout 2000
                          :conn-timeout 2000
                          :body (pr-str judgement)}
                 response (http/post target-url options)]
             response))
         judgements)))

(comment
  (load-judgements-from-feed-file "test/data/rat-dns.json" "http://localhost:4000/" "domain" :domain))

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
                       (partial s/validate sei/IoCIndicator)
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
