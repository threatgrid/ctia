(ns ctia.entity.identity-assertion-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest join-fixtures use-fixtures]]
            [ctia.entity.identity-assertion :refer [identity-assertion-fields]]
            [ctia.test-helpers
             [access-control :refer [access-control-test]]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [post-entity-bulk]]
             [crud :refer [entity-crud-test]]
             [fake-whoami-service :as whoami-helpers]
             [field-selection :refer [field-selection-tests]]
             [http :refer [api-key doc-id->rel-url]]
             [pagination :refer [pagination-test]]
             [store :refer [test-for-each-store]]]
            [ctim.examples.identity-assertions
             :refer
             [new-identity-assertion-maximal new-identity-assertion-minimal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(def new-identity-assertion
  (-> new-identity-assertion-maximal
      (dissoc :id)
      (assoc
       :tlp "green"
       :external_ids
       ["http://ex.tld/ctia/identity-assertion/identity-assertion-123"
        "http://ex.tld/ctia/identity-assertion/identity-assertion-345"])))

(deftest test-identity-assertion-routes
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response api-key
                                         "foouser"
                                         "foogroup"
                                         "user")
     (entity-crud-test
      {:entity "identity-assertion"
       :example new-identity-assertion-maximal
       :invalid-tests? false
       :update-tests? false
       :search-tests? false
       :update-field :source
       :search-field :source
       :headers {:Authorization "45c1f5e3f05d0"}}))))