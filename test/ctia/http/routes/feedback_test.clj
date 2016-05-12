(ns ctia.http.routes.feedback-test
  (:refer-clojure :exclude [get])
  (:require [clojure.test :refer [join-fixtures testing use-fixtures]]
            [ctia.schemas.feedback :refer [NewFeedback]]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers]
             [fake-whoami-service :as whoami-helpers]
             [http :refer [test-get-list test-post]]
             [store :refer [deftest-for-each-store]]]
            [schema-generators.generators :as g]))

(use-fixtures :once (join-fixtures [helpers/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-feedback-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (helpers/set-capabilities! "baruser" "user" #{})
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")
  (whoami-helpers/set-whoami-response "2222222222222" "baruser" "user")

  (testing "Feedback POST and GET on all entities"
    (let [entities ["actor"
                    "campaign"
                    "coa"
                    "exploit-target"
                    "incident"
                    "sighting"
                    "ttp"
                    "judgement"
                    "indicator"]]

      (doall
       (for [entity entities]
         (let [uri (str "/ctia/" entity "/" entity "123/feedback")
               new-feedbacks (->> (g/sample 20 NewFeedback))
               feedbacks (doall (map #(test-post uri %) new-feedbacks))]

           (test-get-list uri feedbacks)))))))
