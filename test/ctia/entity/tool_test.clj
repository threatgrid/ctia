(ns ctia.entity.tool-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest join-fixtures use-fixtures]]
            [ctia.entity.tool.schemas :as ts]
            [ctia.entity.tool :as sut]
            [ctia.test-helpers
             [access-control :refer [access-control-test]]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [POST-entity-bulk]]
             [crud :refer [entity-crud-test]]
             [aggregate :refer [test-metric-routes]]
             [fake-whoami-service :as whoami-helpers]
             [field-selection :refer [field-selection-tests]]
             [http :refer [doc-id->rel-url]]
             [pagination :refer [pagination-test]]
             [store :refer [test-for-each-store-with-app]]]
            [ctim.examples.tools :refer [new-tool-maximal
                                         new-tool-minimal]]
            [ctia.entity.tool.schemas :as ts]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    whoami-helpers/fixture-server]))

(deftest test-tool-routes
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app
                                "foouser"
                                ["foogroup"]
                                "user"
                                all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (entity-crud-test
      (into sut/tool-entity
            {:app app
             :example new-tool-maximal
             :invalid-test-field :title
             :update-field :description
             :headers {:Authorization "45c1f5e3f05d0"}})))))

(deftest test-tool-pagination-field-selection
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [ids (POST-entity-bulk
                app
                new-tool-maximal
                :tools
                30
                {"Authorization" "45c1f5e3f05d0"})]
       (pagination-test
        app
        "ctia/tool/search?query=*"
        {"Authorization" "45c1f5e3f05d0"}
        ts/tool-fields)

       (field-selection-tests
        app
        ["ctia/tool/search?query=*"
         (doc-id->rel-url (first ids))]
        {"Authorization" "45c1f5e3f05d0"}
        ts/tool-fields)))))

(deftest test-tool-routes-access-control
  (access-control-test "tool"
                       new-tool-minimal
                       true
                       true
                       test-for-each-store-with-app))

(deftest test-tool-metric-routes
  (test-metric-routes (into sut/tool-entity
                            {:entity-minimal new-tool-minimal
                             :enumerable-fields sut/tool-enumerable-fields
                             :date-fields sut/tool-histogram-fields})))
