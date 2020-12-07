(ns ctia.store-test
  (:require [ctia.store :as sut]
            [ctia.test-helpers.http :refer [app->APIHandlerServices]]
            [ctia.test-helpers.fixtures :as fixt]
            [ctia.test-helpers.core :as helpers :refer [GET POST PATCH]]
            [ctim.examples.incidents :refer [incident-minimal]]
            [ctia.store-service :as store-svc]
            [clojure.test :refer [deftest testing is]]))

(def ident {:login "johndoe"
            :groups ["Administators"]})

(deftest list-all-pages-test
  (testing "list all pages shall properly list all results for given entity and parameters. "
    (helpers/fixture-ctia-with-app
     (fn [app]
       (let [{:keys [StoreService] :as services} (app->APIHandlerServices app)
             incidents-1 (fixt/n-doc incident-minimal 10)
             incidents-2 (->> (fixt/n-doc incident-minimal 10)
                              (map #(assoc % :source "incident-2-source")))
             incidents (concat incidents-1 incidents-2)
             bulk-result (helpers/POST-bulk app {:incidents incidents})]
         (Thread/sleep 1000) ;; ensure index refresh
         (is
          (= (count incidents)
             (count (sut/list-all-pages :incident
                                        sut/list-fn
                                        {:query "*"}
                                        ident
                                        {}
                                        services))
             (count (sut/list-all-pages :incident
                                        sut/list-fn
                                        {:query "*"}
                                        ident
                                        {:limit 2}
                                        services))
             (count (sut/list-all-pages :incident
                                        sut/list-fn
                                        {:query "*"}
                                        ident
                                        {:limit 3}
                                        services)))
          "paging parameters shall not alter the number of retrieved entities.")
         (is
          (= (count incidents)
             (count (sut/list-all-pages :incident
                                        sut/list-fn
                                        {:query "*"}
                                        ident
                                        {}
                                        services))
             (count (sut/list-all-pages :event
                                        sut/list-events
                                        {:query "*"}
                                        ident
                                        {}
                                        services)))
          "all store shall be properly listed.")
         (is (= (count incidents-2)
                (count (sut/list-all-pages :incident
                                           sut/list-fn
                                           {:one-of {:source "incident-2-source"}}
                                           ident
                                           {}
                                           services)))
             "entities shall be properly filtered."))))))
