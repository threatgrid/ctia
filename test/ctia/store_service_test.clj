(ns ctia.store-service-test
  (:require [ctia.store-service :as sut]))

(defn store-service-map
  "Service map for #'sut/store-service"
  []
  {:StoreService sut/store-service})
