(ns ctia.http.server
  (:require [ctia.http.handler :as handler]
            [ctia.properties :refer [properties]]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.reload :refer [wrap-reload]])
  (:import org.eclipse.jetty.server.Server))

(defonce server (atom nil))

(defn start! [& {:keys [join?]
                 :or {join? true}}]
  (let [{:keys [dev-reload min-threads max-threads port]}
        (get-in @properties [:ctia :http])]
    (reset! server (let [server (jetty/run-jetty (if dev-reload
                                                   (wrap-reload #'handler/app)
                                                   #'handler/app)
                                                 {:port port
                                                  :min-threads min-threads
                                                  :max-threads max-threads
                                                  :join? join?})]
                     (doto server
                       (.setStopAtShutdown true)
                       (.setStopTimeout (* 1000 10)))))))

(defn stop! []
  (swap! server
         (fn [^Server server]
           (when server
               (.stop server))
           nil)))
