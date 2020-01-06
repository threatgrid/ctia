(ns ctia.entity.feed-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest join-fixtures use-fixtures]]
            [ctia.entity.feed :refer [feed-fields]]
            [ctia.test-helpers
             [access-control :refer [access-control-test]]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [post-entity-bulk]]
             [crud :refer [entity-crud-test]]
             [fake-whoami-service :as whoami-helpers]
             [field-selection :refer [field-selection-tests]]
             [http :refer [doc-id->rel-url]]
             [pagination :refer [pagination-test]]
             [store :refer [test-for-each-store]]]))

(def new-feed-maximal
  {:name "name"
   :feed_type "indicator"
   :revision 0
   :indicator_id "https://ex.tld/ctia/indicator/indicator-345"
   :language "en"
   :output :observables
   :lifetime {:start_time #inst "2016-01-01T01:01:01.000Z"
              :end_time #inst "2042-01-01T01:01:01.000Z"}
   :external_ids ["https://ex.tld/ctia/feed/feed-345"]})

(def new-feed-minimal
  {:name "name"
   :feed_type "indicator"
   :output :judgements})

(use-fixtures :once
  (join-fixtures [mth/fixture-schema-validation
                  helpers/fixture-properties:clean
                  whoami-helpers/fixture-server]))

(use-fixtures :each
  whoami-helpers/fixture-reset-state)

(deftest test-feed-routes
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser"
                                ["foogroup"]
                                "user"
                                all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (entity-crud-test
      {:entity "feed"
       :example new-feed-maximal
       :search-field :name
       :update-field :name
       :invalid-test-field :name
       :headers {:Authorization "45c1f5e3f05d0"}}))))

#_(deftest test-feed-pagination-field-selection
    (test-for-each-store
     (fn []
       (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
       (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                           "foouser"
                                           "foogroup"
                                           "user")
       (let [ids (post-entity-bulk
                  (assoc new-feed-maximal :name "foo")
                  :feeds
                  345
                  {"Authorization" "45c1f5e3f05d0"})]

         (field-selection-tests
          ["ctia/feed/search?query=*"
           (doc-id->rel-url (first ids))]
          {"Authorization" "45c1f5e3f05d0"}
          feed-fields))

       (pagination-test
        "ctia/feed/search?query=*"
        {"Authorization" "45c1f5e3f05d0"}
        feed-fields))))

#_(deftest test-feed-routes-access-control
    (test-for-each-store
     (fn []
       (access-control-test "feed"
                            new-feed-minimal
                            true
                            true))))
