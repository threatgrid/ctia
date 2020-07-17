(ns ctia.test-helpers.benchmark
  (:require [clj-momo.lib.net :as net]
            [ctia
             [init :refer [start-ctia!]]
             [shutdown :as shutdown]]
            [ctia.test-helpers
             [core :as helpers]
             [es :as esh]]))

(defn setup-ctia! [fixture]
  (let [http-port (net/available-port)]
    (fixture (fn []
               (helpers/fixture-properties:clean
                #(helpers/with-properties ["ctia.store.es.default.refresh" "false"
                                           "ctia.http.enabled" true
                                           "ctia.http.port" http-port
                                           "ctia.http.bulk.max-size" 100000
                                           "ctia.http.show.port" http-port]
                   (start-ctia! :join? false)))))
    http-port))


(defn setup-ctia-es-store! []
  (setup-ctia! esh/fixture-properties:es-store))

(def delete-store-indexes esh/delete-store-indexes)

(defn cleanup-ctia! []
  (esh/delete-store-indexes false)
  #(shutdown/shutdown-ctia!))
