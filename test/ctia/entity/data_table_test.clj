(ns ctia.entity.data-table-test
  (:require [clj-momo.test-helpers.core :as mth]
            [ctia.entity.data-table :as sut]
            [clojure.test :refer [deftest join-fixtures use-fixtures]]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers]
             [crud :refer [entity-crud-test]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [test-for-each-store-with-app]]]
            [ctim.schemas.common :as c]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    whoami-helpers/fixture-server]))

(def new-data-table
  {:type "data-table"
   :row_count 1
   :external_ids ["http://ex.tld/ctia/data-table/data-table-123"
                  "http://ex.tld/ctia/data-table/data-table-456"]
   :schema_version c/ctim-schema-version
   :tlp "green"
   :timestamp #inst "2016-02-11T00:40:48.212-00:00"
   :columns [{:name "Column1"
              :type "string"}
             {:name "Column2"
              :type "string"}]
   :rows [["foo"] ["bar"]]
   :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                :end_time #inst "2016-07-11T00:40:48.212-00:00"}})

(deftest test-data-table-routes
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (entity-crud-test
      (into sut/data-table-entity
       {:app app
        :example new-data-table
        :invalid-tests? false
        :update-tests? false
        :search-tests? false
        :delete-search-tests? false
        :headers {:Authorization "45c1f5e3f05d0"}})))))
