(ns ctia.import.threatgrid.feed.sightings
  (:require [ctia.import.threatgrid.feed.indicators :as fi]
            [ctim.schemas.sighting :as ss]
            [ctia.lib.time :as time]
            [schema.core :as s]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [clojure.edn :as edn]))

(defn entry->relations
  [feed entry source-observable relation-type related-type related-key]
  (mapv (fn [related-value]
          {:origin (:source feed)
           :origin_uri (:source_uri feed)
           :source source-observable
           :relation relation-type
           :related {:value related-value
                     :type related-type}})
        (related-key entry)))

(defn transform
  [{:keys [title description entries]
    indicator-source :source
    source-uri :source_uri
    {observable-type :type
     observable-field :field
     :as observable} :observable
    {indicator-id :id
     :as indicator} :indicator
    :as feed} & {:keys [tlp confidence]
                 :or {tlp "green"
                      confidence "High"}}]
  (map
   (fn [entry]
     (let [{:keys [description timestamp]} entry
           observable {:type observable-type
                       :value (observable-field entry)}
           feed-meta (dissoc feed :entries)]
       {:description description
        :type "sighting"
        :source (str "Threat Grid " title " feed")
        :source_uri source-uri
        :tlp tlp
        :confidence confidence
        :timestamp timestamp
        :observables [observable]
        :indicators [{:confidence "High"
                      :source indicator-source
                      :indicator_id indicator-id}]
        :relations (entry->relations feed-meta
                                     entry
                                     observable
                                     "Resolved_To"
                                     "ip"
                                     :ips)}))
   entries))

(defn feed->sightings
  [feed & {:as options}]
  (apply transform feed options))
