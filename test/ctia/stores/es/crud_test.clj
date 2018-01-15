(ns ctia.stores.es.crud-test
  (:require [ctia.stores.es.crud :as sut]
            [clojure.test :as t :refer [is testing deftest]]))

(deftest with-errors-test
  (is (= [{:_id 1}
          {:_id 2
           :error "Error message"}]
         (sut/with-errors
           [{:_id 1}
            {:_id 2
             :error "Error message"}]
           {:items [{:index {:_id 1}}
                    {:index {:_id 2 :error "Error message"}}]}))))
