(ns ctia.stores.identity-store-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.store :as store]
            [ctia.store-service :as store-svc]
            [ctia.test-helpers
             [core :as helpers]
             [store :refer [test-for-each-store]]]
            [puppetlabs.trapperkeeper.app :as app]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean]))

(deftest test-read-identity
  (test-for-each-store
   (fn []
    (let [app (helpers/get-current-app)
          store-svc (app/get-service app :StoreService)
          [write-store read-store] (mapv (comp store-svc/store-service-fn->varargs
                                               #(fn [store f]
                                                  (% store-svc store f)))
                                         [store-svc/write-store
                                          store-svc/read-store])]
     (testing "Reading not-found identity returns nil"

       (write-store :identity
                          (fn [store]
                            (store/create-identity store {:login "bar"
                                                          :groups ["foogroup"]
                                                          :capabilities #{:read-actor}
                                                          :role "admin"})))
       (is (nil? (read-store :identity
                                   (fn [store]
                                     (store/read-identity store "foo"))))))))))
