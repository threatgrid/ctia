(ns ctia.stores.es.crud-test
  (:require [ctia.stores.es.crud :as sut]
            [clojure.test :as t :refer [is testing deftest]]))



(deftest partial-results-test
  (is (= {:data [{:error "Exception"}
                 {:id "124"}]}
         (sut/partial-results
          {:es-http-res-body
           {:took 3
            :errors true
            :items [{:index
                     {:_index "website"
                      :_type "blog"
                      :_id "123"
                      :status 400
                      :error "Exception"}}
                    {:index
                     {:_index "website"
                      :_type "blog"
                      :_id "124"
                      :_version 5
                      :status 200}}]}}
          [{:_id "123"
            :id "123"}
           {:_id "124"
            :id "124"}]
          identity))))
