(ns ctia.graphql-named-type-registry-service-core
  (:require [ctia.schemas.graphql.helpers :as helpers]))

(defn get-or-update-named-type-registry [{:keys [type-registry]} nme f]
  (helpers/get-or-update-named-type-registry type-registry nme f))

(defn start [context]
  (assoc context :type-registry (atom {})))
