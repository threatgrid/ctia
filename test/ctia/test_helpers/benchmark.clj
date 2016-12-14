(ns ctia.test-helpers.benchmark
  (:require [clj-momo.lib.net :as net]
            [ctia.init :refer [start-ctia!]]
            [ctia.flows.hooks :as hooks]
            [ctia.http.server :as http-server]
            [ctia.shutdown :as shutdown]
            [ctia.test-helpers
             [core :as helpers]
             [es :as esh]]))

(defn setup-ctia! [fixture]
  (let [http-port (net/available-port)]
    (println "Default: Launch CTIA on port" http-port)
    (fixture (fn [] (helpers/fixture-properties:clean
                    #(helpers/with-properties ["ctia.store.es.default.refresh" "false"
                                               "ctia.http.enabled" true
                                               "ctia.http.port" http-port
                                               "ctia.http.show.port" http-port]
                       (start-ctia! :join? false)))))
    http-port))


(defn setup-ctia-es-store! []
  (setup-ctia! esh/fixture-properties:es-store))

(defn cleanup-ctia! [_]
  (shutdown/shutdown-ctia!))
