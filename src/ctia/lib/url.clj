(ns ctia.lib.url
  (:require [ring.util.codec :as c]))

(def url-chars-re #"[-A-Za-z0-9._~:/?#\[\]@!$&'()*+,;=%]+")

(def encode c/url-encode)

(def decode c/url-decode)

(defn encoded? [s]
  (boolean
   (re-matches url-chars-re s)))
