(ns ctia.http.handler-test
  (:require [ctia.http.handler :as sut]
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
