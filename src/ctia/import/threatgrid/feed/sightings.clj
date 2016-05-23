(ns ctia.import.threatgrid.feed.sightings
  (:require [ctia.import.threatgrid.feed.indicators :as fi]
            [ctia.schemas.sighting :as ss]
            [ctia.lib.time :as time]
            [schema.core :as s]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [clojure.edn :as edn]))

(defn make-feed-sighting
  [description feed-uri timestamp observable indicator]
  (let [sighting {:description description
                  :type "sighting"
                  :source "ThreatGrid Feed"
                  :source_uri feed-uri
                  :tlp "green"
                  :confidence "High"
                  :timestamp timestamp
                  :observables [observable]
                  :indicators [indicator]}]))

(defn transform
  [entries
   observable-type
   observable-value
   indicator-id
   indicator-source
   source-uri & {:keys [tlp confidence]
                 :or {tlp "green"
                      confidence "High"}}]
  (map
   (fn [entry]
     (let [{:keys [description timestamp]} entry]
       {:description description
        :type "sighting"
        :source "ThreatGrid"
        :source_uri source-uri
        :tlp tlp
        :confidence confidence
        :timestamp timestamp
        :observables [{:type observable-type
                       :value (observable-value entry)}]
        :indicators [{:confidence "High"
                      :source indicator-source
                      :indicator_id indicator-id}]}))
   entries))

(defn entries->sightings
  [entries observable-type observable-field
   indicator-id indicator-source source-uri & {:as options}]
  (apply transform entries observable-type observable-field
         indicator-id indicator-source source-uri options))

(defn feed->sightings
  [feed]
  (let [{:keys [entries source-uri title]
         {observable-type :type
          observable-field :field} :observable
         {indicator-id :id
          indicator-source :title} :indicator} feed
        sighting-source (str "ThreatGrid " title " Feed")]
    (entries->sightings entries
                        observable-type
                        observable-field
                        indicator-id
                        indicator-source
                        source-uri)))

(defn feed-file->sightings
  [feed-name file observable-type observable-field ctia-uri source-uri & {:as options}]
  (let [entries (json/parse-string (slurp file) true)
        description (-> entries first :description)
        indicator (fi/feed-indicator feed-name description ctia-uri)
        indicator-id (:id indicator)
        indicator-source (:title indicator)]
    (entries->sightings entries
                        observable-type
                        observable-field
                        indicator-id
                        indicator-source
                        source-uri
                        options)))
