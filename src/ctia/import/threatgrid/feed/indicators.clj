(ns ctia.import.threatgrid.feed.indicators
  (:require [ctim.schemas.indicator :as si]
            [schema.core :as s]
            [clj-http.client :as http]
            [cheshire.core :as cjson]
            [clojure.edn :as edn]))

(defn http-options []
  {:content-type :edn
   :accept :edn
   :throw-exceptions false
   :socket-timeout 10000
   :conn-timeout 10000})

(defn stored-ctia-indicator [title ctia-uri]
  (let [target-url (str ctia-uri "/ctia/indicator/title/" title)
        result (http/get target-url (http-options))]
    (cond
      (= 200 (:status result)) (-> result
                                   :body
                                   edn/read-string
                                   first)
      (= 400 (:status result)) nil)))

(defn make-ctia-indicator
  [title description ctia-uri]
  (let [indicator {:title (str "tg-feed-" title)
                   :type "indicator"
                   :short_description (str "ThreatGrid " title " Feed.")
                   :description (str description)
                   :confidence "High"
                   ;; :severity 100
                   :tags []
                   :producer "ThreatGrid"}
        target-url (str ctia-uri "/ctia/indicator")
        options (assoc (http-options) :body (pr-str indicator))
        response (http/post target-url options)
        result (-> response
                   :body
                   edn/read-string)]
    (println "Created" (:id result))
    result))

(defn init-ctia-indicator
  [title description ctia-uri indicators]
  (let [ctia-indicator (or (stored-ctia-indicator title ctia-uri)
                           (make-ctia-indicator title description ctia-uri))]
    (swap! indicators assoc title ctia-indicator)
    ctia-indicator))

(defn feed-indicator
  [title description ctia-uri indicators]
  (or (get-in @indicators [title])
      (init-ctia-indicator title description ctia-uri indicators)))
