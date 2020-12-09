(ns ctia.stores.identity-store-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [ctia.store :as store]
            [ctia.test-helpers
             [core :as helpers]
             [store :refer [test-for-each-store-with-app]]]
            [puppetlabs.trapperkeeper.app :as app]))

(use-fixtures :once mth/fixture-schema-validation)

(deftest test-read-identity
  (test-for-each-store-with-app
   (fn [app]
    (let [{{:keys [read-store]} :StoreService} (app/service-graph app)]
     (testing "Reading not-found identity returns nil"
       (-> (read-store :identity)
           (store/create-identity {:login "bar"
                                   :groups ["foogroup"]
                                   :capabilities #{:read-actor}
                                   :role "admin"}))
       (is (nil? (-> (read-store :identity)
                     (store/read-identity "foo")))))))))
