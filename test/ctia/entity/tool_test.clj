(ns ctia.entity.tool-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest join-fixtures use-fixtures]]
            [ctia.entity.tool :as sut]
            [ctia.test-helpers.access-control :refer [access-control-test]]
            [ctia.test-helpers.auth :refer [all-capabilities]]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.crud :refer [entity-crud-test]]
            [ctia.test-helpers.aggregate :refer [test-metric-routes]]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [ctia.test-helpers.store :refer [test-for-each-store-with-app]]
            [ctim.examples.tools :refer [new-tool-maximal new-tool-minimal]]))

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
