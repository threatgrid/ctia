(ns ctia.store-service-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [ctia.store-service :as sut]
            [ctia.stores.es.init :as es-init]
            [ctia.test-helpers.core :as th]
            [ctia.test-helpers.es :as es-th]
            [ctia.test-helpers.store :refer [test-for-each-store-with-app]]
            [ductile.conn :as es-conn]
            [ductile.index :as index]
            [puppetlabs.trapperkeeper.app :as app]))

(use-fixtures :once mth/fixture-schema-validation)

(defn store-service-map
  "Service map for #'sut/store-service"
  []
  {:StoreService sut/store-service})

(deftest disabled-initialization-test
  (doseq [disable? [true false]]
    (testing disable?
      (th/with-properties ["ctia.features.disable" (if disable? "asset" "")]
        (test-for-each-store-with-app
          (fn [app]
            (let [conn (es-conn/connect (es-init/get-store-properties
                                          ::no-store
                                          (get-in (app/service-graph app) [:ConfigService :get-in-config])))]
              (try
                (is (= disable? (not (index/get conn (es-th/get-indexname app :asset)))))
                (finally
                  (es-conn/close conn))))))))))
