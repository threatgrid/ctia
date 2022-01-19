(ns ctia.http.exceptions-test
  (:require  [ctia.http.exceptions :as sut]
             [clojure.test :refer [deftest is]]))

(deftest es-ex-data-test
  (let [exdata {:es "don't like it"}
        data {:some :stuff}
        exception (ex-info "ES is not happy"
                           exdata)
        query-params {:query "http://very.bad"}
        request {:authorization "Dont log me"
                 :query-params query-params}]
    (is (= {:query-params query-params
            :data data
            :ex-data exdata}
           (sut/es-ex-data exception data request)))))
