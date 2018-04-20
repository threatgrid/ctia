(ns ctia.http.routes.feedback-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [get]]
             [crud :refer [entity-crud-test]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [test-for-each-store]]]
            [ctim.domain.id :as id]))

(def new-feedback
  {:feedback -1
   :schema_version schema-version
   :entity_id "judgement-123"
   :external_ids ["http://ex.tld/ctia/feedback/feedback-123"
                  "http://ex.tld/ctia/feedback/feedback-456"]
   :type "feedback"
   :reason "false positive"
   :tlp "green"})

(defn feedback-by-entity-id-test
  [feedback-id feedback]
  (testing "GET /ctia/feedback?entity_id="
    (let [response (get (str "ctia/feedback")
                        :query-params {:entity_id "judgement-123"}
                        :headers {"Authorization" "45c1f5e3f05d0"})
          feedbacks (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (deep=
           [(assoc new-feedback :id (id/long-id feedback-id))]
           feedbacks)))))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest test-feedback-routes
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (helpers/set-capabilities! "baruser" ["bargroup"] "user" #{})
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (whoami-helpers/set-whoami-response "2222222222222"
                                         "baruser"
                                         "bargroup"
                                         "user")

     (entity-crud-test
      {:entity "feedback"
       :example new-feedback
       :update-tests? false
       :invalid-tests? false
       :search-tests? false
       :additional-tests feedback-by-entity-id-test
       :headers {:Authorization "45c1f5e3f05d0"}}))))

