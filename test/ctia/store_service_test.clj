(ns ctia.store-service-test
  (:require [ctia.store-service :as sut]))

(defn store-services
  "Service map for #'sut/store-service"
  []
  {:StoreService sut/store-service})
