(ns ctia.ductile-service-test
  (:require [compojure.api.core :refer [GET]]
            [clojure.test :refer [deftest is testing]]
            [ctia.ductile-service :as ductile-svc]
            [puppetlabs.trapperkeeper.app :as app]
            [puppetlabs.trapperkeeper.testutils.bootstrap :refer [with-app-with-config]]
            [ring.adapter.jetty :as jetty]
            [ring.util.http-response :refer [ok]])
  (:import [org.eclipse.jetty.server Server]
           [java.util UUID]))

(defn gen-services []
  [ductile-svc/ductile-service])

(defn gen-config []
  {})

(deftest request-fn-test
  (testing ":request-fn acts like clj-http.client/request"
    (with-app-with-config app (gen-services) (gen-config)
      (let [{{:keys [request-fn]} :DuctileService} (app/service-graph app)
            uuid (str (UUID/randomUUID))
            server (jetty/run-jetty
                     (GET "/" [] (ok uuid))
                     {:port 0
                      :join? false})]
        (try
          (is (= (-> {:method :get
                      :url (-> server .getURI str)}
                     request-fn 
                     :body)
                 uuid))
          (finally
            (.stop server)))))))
