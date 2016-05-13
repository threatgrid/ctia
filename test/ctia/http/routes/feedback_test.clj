(ns ctia.http.routes.feedback-test
  (:refer-clojure :exclude [get])
  (:import java.util.UUID)
  (:require [clojure.test :refer [join-fixtures testing use-fixtures]]
            [ctia.domain.id :as id]
            [ctia.properties :refer [properties]]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers]
             [fake-whoami-service :as whoami-helpers]
             [http :refer [test-post assert-post test-get-list]]
             [store :refer [deftest-for-each-store]]]))

(use-fixtures :once (join-fixtures [helpers/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(def entities ["actor"
               "campaign"
               "coa"
               "exploit-target"
               "incident"
               "sighting"
               "ttp"
               "judgement"
               "indicator"])

(deftest-for-each-store test-feedback-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (helpers/set-capabilities! "baruser" "user" #{})
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")
  (whoami-helpers/set-whoami-response "2222222222222" "baruser" "user")


  (doseq [entity entities]

    (testing (str "Feedback POST and GET on " entity)
      (let [short-id (str entity "-" (UUID/randomUUID))
            new-feedbacks (->> {:feedback -1,
                                :reason "false positive"
                                :tlp "green"}
                               (repeat 5))

            uri (str "/ctia/" entity "/" short-id "/feedback")
            feedbacks (doall (map #(assert-post uri %) new-feedbacks))]

        (test-get-list uri feedbacks)))))
