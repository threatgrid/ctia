(ns ctia.entity.feedback-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [GET]]
             [crud :refer [entity-crud-test]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [test-for-each-store-with-app]]]
            [ctim.domain.id :as id]))

(def new-feedback
  {:feedback -1
   :schema_version schema-version
   :entity_id "judgement-123"
   :external_ids ["http://ex.tld/ctia/feedback/feedback-123"
                  "http://ex.tld/ctia/feedback/feedback-456"]
   :type "feedback"
   :reason "false positive"
   :timestamp #inst "2042-01-01T00:00:00.000Z"
   :tlp "green"})

(defn feedback-by-entity-id-test
  [app feedback-id _]
  (testing "GET /ctia/feedback?entity_id="
    (let [response (GET app
                        (str "ctia/feedback")
                        :query-params {:entity_id "judgement-123"}
                        :headers {"Authorization" "45c1f5e3f05d0"})
          feedbacks (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= [(assoc new-feedback :id (id/long-id feedback-id))]
             (map #(dissoc % :owner :groups) feedbacks))))))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(deftest test-feedback-routes
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (helpers/set-capabilities! app "baruser" ["bargroup"] "user" #{})
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (whoami-helpers/set-whoami-response app
                                         "2222222222222"
                                         "baruser"
                                         "bargroup"
                                         "user")

     (entity-crud-test
      {:app app
       :entity "feedback"
       :example new-feedback
       :update-tests? false
       :invalid-tests? false
       :search-tests? false
       :additional-tests feedback-by-entity-id-test
       :headers {:Authorization "45c1f5e3f05d0"}}))))

