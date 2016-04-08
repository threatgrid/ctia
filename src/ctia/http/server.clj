(ns ctia.http.server
  (:require [ctia.http.handler :as handler]
            [ctia.properties :refer [properties]]
            [ring.adapter.jetty :as jetty])
  (:import org.eclipse.jetty.server.Server))

(defonce server (atom nil))

(defn start! [& {:keys [join?]
                 :or {join? true}}]
  (let [{:keys [min-threads max-threads port]} (get-in @properties [:ctia :http])]
    (reset! server (jetty/run-jetty handler/app
                                    {:port port
                                     :min-threads min-threads
                                     :max-threads max-threads
                                     :join? join?}))))

(defn stop! []
  (swap! server
         (fn [^Server s]
           (when s (.stop s))
           nil)))
