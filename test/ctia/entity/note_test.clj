(ns ctia.entity.note-test
  (:require [clojure.test :refer [deftest join-fixtures use-fixtures]]
            [ctia.entity.note :as sut]
            [ctia.entity.note.schemas :as note-schemas]
            [ctia.test-helpers.access-control :refer [access-control-test]]
            [ctia.test-helpers.aggregate :refer [test-metric-routes]]
            [ctia.test-helpers.auth :refer [all-capabilities]]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.crud :refer [entity-crud-test]]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [ctia.test-helpers.store :refer [test-for-each-store-with-app]]
            [ctim.examples.notes :refer [new-note-maximal new-note-minimal]]
            [schema.test :refer [validate-schemas]]))

(use-fixtures :once (join-fixtures [validate-schemas
                                    whoami-helpers/fixture-server]))

(deftest test-note-crud-routes
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (entity-crud-test
      (into sut/note-entity
            {:app app
             :example new-note-maximal
             :update-field :content
             :invalid-tests? false
             :search-field :content
             :headers {:Authorization "45c1f5e3f05d0"}})))))

(deftest test-note-routes-access-control
  (access-control-test "note"
                       new-note-minimal
                       true
                       true
                       test-for-each-store-with-app))

(deftest test-note-metric-routes
  (test-metric-routes (into sut/note-entity
                            {:entity-minimal new-note-minimal
                             :enumerable-fields note-schemas/note-enumerable-fields
                             :date-fields note-schemas/note-histogram-fields})))
