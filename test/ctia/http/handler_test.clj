(ns ctia.http.handler-test
  (:require [ctia.http.handler :as sut]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.http :refer [app->APIHandlerServices]]
            [clojure.string :as str]
            [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]
            [compojure.api.api :refer [api]]
            [compojure.api.core :refer [GET]]
            [compojure.api.routes :as routes]
            [compojure.api.swagger :as swagger]))

;; regression test for implementation details of compojure-api
;; used to implement sut/add-dynamic-tags
(deftest add-dynamic-tags-test
  (is (= (routes/get-routes
           (api
             (swagger/swagger-routes)
             (sut/add-dynamic-tags
               (GET "/" [] nil)
               ["foo"])))

         [["/swagger.json" :get {:x-no-doc true,
                                 :x-name :compojure.api.swagger/swagger}]
          ["/" :get
           ;; this is the important part. when updating this test,
           ;; make sure {:tags #{"foo"}} is where it's supposed to be!
           {:tags #{"foo"}}]])))

(deftest api-handler-swagger-test
  (helpers/fixture-ctia-with-app
    {:disable-http true}
    (fn [app]
      (let [;; these routes don't have descriptions (yet)
            expected-no-doc #{"/swagger.json"
                              "/doc/*.*"
                              "/ctia/feed/:id/view.txt"
                              "/ctia/feed/:id/view"
                              "/ctia/version"
                              "/ctia/status"}
            actual-no-doc (->> 
                            (sut/api-handler (app->APIHandlerServices app))
                            routes/get-routes
                            routes/ring-swagger-paths
                            :paths
                            (remove (fn [[_ m]]
                                      (every? (comp seq :description) (vals m))))
                            (map first)
                            set)]
        (is (= expected-no-doc actual-no-doc)
            (let [missing-docs (sort (set/difference actual-no-doc expected-no-doc))
                  extra-docs (sort (set/difference expected-no-doc actual-no-doc))]
              (str
                (when (seq missing-docs)
                  (str "Consider adding a :description with capabilities to these routes: "
                       (str/join ", " missing-docs)
                       "\n\n"))
                (when (seq extra-docs)
                  (str "Expected no :description on these routes, but found some: "
                       (str/join ", " extra-docs))))))))))
