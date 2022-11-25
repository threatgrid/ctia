(ns ctia.stores.es.store-test
  (:require [ctia.store :as store]
            [ctia.stores.es.store :as sut]
            [ctia.test-helpers.http :refer [app->APIHandlerServices]]
            [ctia.test-helpers.fixtures :as fixt]
            [ctia.test-helpers.core :as helpers]
            [ctim.examples.incidents :refer [incident-minimal]]
            [clojure.test :refer [deftest testing is]]))

(def admin-ident {:login "johndoe"
                  :groups ["Administators"]})

(deftest all-pages-iteration-test
  (testing "all pages shall properly list all results for given entity and parameters."
    (helpers/fixture-ctia-with-app
     (fn [app]
       (let [{{:keys [get-store]} :StoreService} (app->APIHandlerServices app)
             incidents-1 (fixt/n-doc incident-minimal 10)
             incidents-2 (->> (fixt/n-doc incident-minimal 10)
                              (map #(assoc % :source "incident-2-source")))
             incidents (concat incidents-1 incidents-2)
             _ (helpers/POST-bulk app {:incidents incidents})
             incident-store (get-store :incident)
             event-store (get-store :event)]
         (Thread/sleep 1500) ;; ensure index refresh

         (letfn [(make-query-fn [store filters identity-map]
                   (fn query-fn [query-params]
                     (store/list-records store
                                         filters
                                         identity-map
                                         query-params)))]

           (let [query-incidents (make-query-fn
                                  incident-store
                                  {:query "*"}
                                  admin-ident)
                 query-events (make-query-fn
                               event-store
                               {:query "*"}
                               admin-ident)]
             (is
              (= (count incidents)
                 (count (sut/all-pages-iteration query-incidents {}))
                 (count (sut/all-pages-iteration query-incidents {:limit 2}))
                 (count (sut/all-pages-iteration query-incidents {:limit 3})))
              "paging parameters shall not alter the number of retrieved entities.")
             (is
              (= (count incidents)
                 (count (sut/all-pages-iteration query-incidents {}))
                 (count (sut/all-pages-iteration query-events {})))
              "all store shall be properly listed."))

           (let [query-incidents (make-query-fn
                                  incident-store
                                  {:one-of {:source "incident-2-source"}}
                                  admin-ident)]
             (is (= (count incidents-2)
                    (count (sut/all-pages-iteration query-incidents {})))
                 "entities shall be properly filtered."))))))))
