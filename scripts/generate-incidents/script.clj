(ns generate-incidents.script
  (:require [clojure.pprint :as pprint]
            [babashka.curl :as curl]
            [babashka.http-client :as http]
            [cheshire.core :as json]))

;; Couple of incidents

(def incidents-data
  {:description       "incidents to test"
   :incidents
   [{:assignees           []
     :categories          ["Denial of Service" "Improper Usage"]
     :confidence          "High"
     :description         "description of first incident"
     :discovery_method    "Log Review"
     :external_ids        []
     :external_references []
     :incident_time       {:closed     "2023-06-16T00:40:48Z"
                           :discovered "2023-06-14T00:40:48Z"
                           :opened     "2023-06-15T16:40:48Z"
                           :rejected   "2023-06-11T00:40:48Z"
                           :remediated "2023-06-11T00:40:48Z"
                           :reported   "2023-06-11T00:40:48Z"}
     :intended_effect     "Extortion"
     :language            "language"
     :revision            1
     :schema_version      "1.1.3"
     :short_description   "first incident"
     :source              "source"
     :source_uri          "http://example.com/incident-one"
     :status              "Open"
     :timestamp           "2023-06-11T00:40:48Z"
     :title               "alpha one"
     :tlp                 "green"
     :type                "incident"}
    {:assignees           []
     :categories          ["Denial of Service" "Improper Usage"]
     :confidence          "High"
     :description         "second incident"
     :discovery_method    "Log Review"
     :external_ids        []
     :external_references []
     :incident_time       {:closed     "2023-06-16T00:40:48Z"
                           :discovered "2023-06-14T00:40:48Z"
                           :opened     "2023-06-17T15:40:48Z"
                           :rejected   "2023-06-11T00:40:48Z"
                           :remediated "2023-06-11T00:40:48Z"
                           :reported   "2023-06-11T00:40:48Z"}
     :intended_effect     "Extortion"
     :language            "language"
     :revision            1
     :schema_version      "1.1.3"
     :short_description   "incident numero does"
     :source              "source"
     :source_uri          "http://example.com/incident-two"
     :status              "Open"
     :timestamp           "2023-06-11T00:40:48Z"
     :title               "alpha two"
     :tlp                 "green"
     :type                "incident"}
    #_{:assignees           []
     :categories          ["Denial of Service" "Improper Usage"]
     :confidence          "High"
     :description         "third incident"
     :discovery_method    "Log Review"
     :external_ids        []
     :external_references []
     :incident_time       {:closed     "2023-06-11T00:40:48Z"
                           :discovered "2023-06-11T00:40:48Z"
                           :opened     "2023-06-11T00:40:48Z"
                           :rejected   "2023-06-11T00:40:48Z"
                           :remediated "2023-06-11T00:40:48Z"
                           :reported   "2023-06-11T00:40:48Z"}
     :intended_effect     "Extortion"
     :language            "language"
     :revision            1
     :schema_version      "1.1.3"
     :short_description   "incident numero tres"
     :source              "source"
     :source_uri          "http://example.com/incident-three"
     :status              "Open"
     :timestamp           "2023-06-11T00:40:48Z"
     :title               "beta one"
     :tlp                 "green"
     :type                "incident"}
    #_{:assignees           []
     :categories          ["Denial of Service" "Improper Usage"]
     :confidence          "High"
     :description         "fourth incident"
     :discovery_method    "Log Review"
     :external_ids        []
     :external_references []
     :incident_time       {:closed     "2023-06-11T00:40:48Z"
                           :discovered "2023-06-11T00:40:48Z"
                           :opened     "2023-06-11T00:40:48Z"
                           :rejected   "2023-06-11T00:40:48Z"
                           :remediated "2023-06-11T00:40:48Z"
                           :reported   "2023-06-11T00:40:48Z"}
     :intended_effect     "Extortion"
     :language            "language"
     :revision            1
     :schema_version      "1.1.3"
     :short_description   "incident numero tres"
     :source              "source"
     :source_uri          "http://example.com/incident-four"
     :status              "Open"
     :timestamp           "2023-06-11T00:40:48Z"
     :title               "beta two"
     :tlp                 "green"
     :type                "incident"}
    ]
   :language          "language"
   :revision          1
   :schema_version    "1.1.3"
   :short_description "bundle"
   :source            "source"
   :source_uri        "http://example.com"
   :timestamp         "2023-06-11T00:40:48Z"
   :title             "title"
   :tlp               "green"
   :valid_time        {:end_time   "2016-07-11T00:40:48Z"
                       :start_time "2016-05-11T00:40:48Z"}})

(def client-id+secret
  {:client_id "client-"
   :client_secret ""})

(def api-root "https://visibility.int.iroh.site")

(defn get-jwt []
  #_(->
   (http/post
    (format "%s/iroh/oauth2/token" api-root)
    {:headers {"content-type" "application/x-www-form-urlencoded"}
     :accept :json
     :form-params (merge
                   client-id+secret
                   {:grant_type "client_credentials"})})
   :body
   (json/parse-string true)
   :access_token)
  (System/getenv "SECUREX_BEARER_TOKEN"))

#_(format "Bearer %s" (get-jwt))

(defn import-incidents []
  (->
   (http/post
    (format "%s/iroh/private-intel/bundle/import" api-root)
    {:headers {"Content-Type" "application/json"
               "Authorization" (format "Bearer %s" (get-jwt))}
     :body (json/encode incidents-data)})
   :body
   (json/parse-string true)))

;; (import-incidents)

(defn find-incidents []
  (->
   (http/get
    (format "%s/iroh/private-intel/incident/search?from=2023-05-16T21:47:00.000Z" api-root)
    {:headers {"Content-Type" "application/json"
               "Authorization" (format "Bearer %s" (get-jwt))}})
   :body
   (json/parse-string true)))

;; (find-incidents)

(defn get-incident [incident-id]
  (->
   (http/get
    (format "%s/iroh/private-intel/incident/%s" api-root incident-id)
    {:headers {"Content-Type" "application/json"
               "Authorization" (format "Bearer %s" (get-jwt))}})
   :body
   (json/parse-string true)))

#_(pprint/pprint (get-incident "incident-98b203a4-4b5e-466b-aa31-d876b7841774"))

;; -------------------
;; commands to execute
;; -------------------

(pprint/pprint (import-incidents))
#_(pprint/pprint (find-incidents))

;; -------------------
;; to run the script:
;;
;; $ bb -f script.clj
;;
;; -------------------
