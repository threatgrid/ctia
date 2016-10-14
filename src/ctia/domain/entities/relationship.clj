(ns ctia.domain.entities.relationship
  (:require
   [ctia.properties :refer [get-http-show]]
   [ctim.domain.id :as id]))

(def short-id->long-id
  (id/factory:short-id->long-id :relationship get-http-show))

(defn with-long-id [entity]
  (update entity :id short-id->long-id))

(defn page-with-long-id [m]
  (update m :data (partial map with-long-id)))
