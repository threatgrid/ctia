(ns ctia.stores.sparql.store-test
  (:require [ctia.stores.sparql.store :as sut]
            [ctia.store :refer [list-records]]
            [clojure.test :refer [deftest is testing]]))

(deftest sparql-test
  (let [sparql-store (sut/->SPARQLStore "http://localhost:8890/sparql")]
    (clojure.pprint/pprint
     (list-records sparql-store {} {} {}))
  ))
