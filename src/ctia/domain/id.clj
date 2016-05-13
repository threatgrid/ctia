(ns ctia.domain.id
  (:require [ctia.lib.url :as url])
  (:import java.lang.String))

(def short-id-re
  (re-pattern (str "(" url/url-chars-re ")")))

(def url-re
  #"(https?):\/\/([-\da-zA-Z][-\da-zA-Z.]*)(:(\d+))?((\/[-\w.]+)*)\/ctia\/([a-z]+)\/")

(def long-id-re
  (re-pattern
   (str url-re
        short-id-re)))

(defn make-long-id-str
  [{:keys [hostname short-id path-prefix port protocol type]
    :as parts}]
  (when parts
    (str protocol
         "://"
         hostname
         (if port (str ":" port))
         path-prefix
         "/ctia/"
         type
         "/"
         (url/encode short-id))))

(defn long-id?
  "Make an educated guess if this is a long-form ID"
  [^String id]
  ;; TODO - More specific checking?
  (.startsWith id "http"))

(def short-id?
  "Make an educated guess if this is a short-form ID"
  (complement long-id?))

(defprotocol ID
  (localize [this url-params]
    "Localize this ID")
  (short-id [this]
    "The short part of the ID")
  (long-id [this] [this url-params]
    "Convert this record to a long ID string"))

(defrecord CtiaId [hostname short-id path-prefix port protocol type]
  ID
  (localize [this url-show-params]
    (merge this
           (update url-show-params :path-prefix not-empty)))
  (short-id [this]
    short-id)
  (long-id [this]
    (make-long-id-str this))
  (long-id [this url-show-params]
    (long-id (localize this url-show-params))))

(defn long-id->id
  [long-id]
  (if-let [[_ proto host _ port path _ type id] (re-matches long-id-re long-id)]
    (map->CtiaId
     {:hostname host
      :short-id (url/decode id)
      :path-prefix (not-empty path)
      :port (some-> port (Integer/parseInt))
      :protocol proto
      :type (name type)})))

(defn short-id->id
  [type short-id {:keys [hostname path-prefix port protocol]}]
  (map->CtiaId
   {:hostname hostname
    :short-id short-id
    :path-prefix (not-empty path-prefix)
    :port port
    :protocol protocol
    :type (name type)}))

(defn ->id
  "Given a string ID, build an ID instance with provided URL
   parameters.  Also ensures that the ID is encoded."
  [type id url-show-params]
  (if (long-id? id)
    (localize (long-id->id id)
              url-show-params)
    (short-id->id (name type)
                  id
                  url-show-params)))

(defn str->short-id
  "Given an unknown ID string (presumably a user provided ID string,
  which may be a URL) parse the string and return just the short form
  ID."
  [s]
  (if (long-id? s)
    (last (re-matches long-id-re s))
    s))

(defn long-id-factory
  "Build a fn that takes a short-id and returns a long-id"
  [type url-params-fn]
  (fn [short-id]
    (long-id
     (short-id->id type
                   short-id
                   (url-params-fn)))))
