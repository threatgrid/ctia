(ns ctia.http.routes.data-table-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [join-fixtures use-fixtures]]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers]
             [crud :refer [entity-crud-test]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [deftest-for-each-store]]]
            [ctim.schemas.common :as c]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(def new-data-table
  {:type "data-table"
   :row_count 1
   :external_ids ["http://ex.tld/ctia/data-table/data-table-123"
                  "http://ex.tld/ctia/data-table/data-table-456"]
   :schema_version c/ctim-schema-version
   :tlp "green"
   :columns [{:name "Column1"
              :type "string"}
             {:name "Column2"
              :type "string"}]
   :rows [["foo"] ["bar"]]
   :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                :end_time #inst "2016-07-11T00:40:48.212-00:00"}})

(deftest-for-each-store test-data-table-routes
  (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                      "foouser"
                                      "foogroup"
                                      "user")
  (entity-crud-test {:entity "data-table"
                     :example new-data-table
                     :invalid-tests? false
                     :update-tests? false
                     :search-tests? false
                     :headers {:Authorization "45c1f5e3f05d0"}}))
