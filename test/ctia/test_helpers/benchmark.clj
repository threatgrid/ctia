(ns ctia.test-helpers.benchmark
  (:require [ctia
             [events :as events]
             [init :refer [start-ctia!]]]
            [ctia.flows.hooks :as hooks]
            [ctia.http.server :as http-server]
            [ctia.test-helpers
             [core :as helpers]
             [es :as esh]]))

(defn setup-ctia! [fixture]
  (let [http-port (helpers/available-port)]
    (println "Default: Launch CTIA on port" http-port)
    (fixture (fn [] (helpers/fixture-properties:clean
                    #(helpers/with-properties ["ctia.store.es.default.refresh" false
                                               "ctia.http.enabled" true
                                               "ctia.http.port" http-port
                                               "ctia.http.show.port" http-port]
                       (start-ctia! :join? false)))))
    http-port))


(defn setup-ctia-atom-store! []
  (setup-ctia! helpers/fixture-properties:atom-store))

(defn setup-ctia-es-store! []
  (setup-ctia! esh/fixture-properties:es-store))

(defn setup-ctia-es-store-native! []
  (setup-ctia! esh/fixture-properties:es-store-native))

(defn cleanup-ctia! [_]
  (http-server/stop!)
  (hooks/shutdown!)
  (events/shutdown!))
