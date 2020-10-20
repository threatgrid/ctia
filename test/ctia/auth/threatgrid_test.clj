(ns ctia.auth.threatgrid-test
  (:require [ctia.auth.threatgrid :as sut]
            [ctia.store-service-test :refer [store-service-map]]
            [clojure.test :refer [deftest is testing]]
            [puppetlabs.trapperkeeper.app :as app]
            [puppetlabs.trapperkeeper.testutils.bootstrap :refer [with-app-with-config]])
  (:import [java.util UUID]))

(defn threatgrid-auth-whoami-url-service-map
  "Service map for #'sut/threatgrid-auth-whoami-url-service"
  []
  {:ThreatgridAuthWhoAmIURLService sut/threatgrid-auth-whoami-url-service})

(defn threatgrid-auth-service-map
  "Service map for #'sut/threatgrid-auth-service"
  []
  (merge (threatgrid-auth-whoami-url-service-map)
         (store-service-map)
         {:IAuth sut/threatgrid-auth-service}))

(deftest threatgrid-auth-whoami-url-service-test
  (doseq [url (doto (not-empty
                      (repeatedly 10
                                  #(str "https://cisco.com/" (UUID/randomUUID))))
                (assert "Must define cases"))
          :let [services (vals (threatgrid-auth-whoami-url-service-map))]]
    (testing ":whoami-url configuration corresponds to (get-whoami-url)"
      (with-app-with-config app services
        {:ctia {:auth {:threatgrid {:whoami-url url}}}}
        (let [{{:keys [get-whoami-url]} :ThreatgridAuthWhoAmIURLService} (app/service-graph app)]
          (is (= (get-whoami-url)
                 url)))))))
