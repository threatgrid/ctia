(ns ctia.link.routes-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctim.domain.id :refer [long-id->id]]
            [ctia.entity.relationship :refer [relationship-fields]]
            [ctia.test-helpers
             [access-control :refer [access-control-test]]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [post post-entity-bulk]]
             [crud :refer [entity-crud-test]]
             [fake-whoami-service :as whoami-helpers]
             [field-selection :refer [field-selection-tests]]
             [http :refer [doc-id->rel-url]]
             [pagination :refer [pagination-test]]
             [store :refer [test-for-each-store]]]
            [ctim.examples.incidents
             :refer
             [new-incident-minimal]]
            [ctim.examples.casebooks
             :refer
             [new-casebook-minimal]]
            [ctim.examples.relationships
             :refer
             [new-relationship-minimal]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest links-routes-test
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (testing "Indicator & Casebook test setup"
       (let [{casebook-body :parsed-body
              casebook-status :status}
             (post "ctia/casebook"
                   :body new-casebook-minimal
                   :headers {"Authorization" "45c1f5e3f05d0"})
             {incident-body :parsed-body
              incident-status :status}
             (post "ctia/incident"
                   :body new-incident-minimal
                   :headers {"Authorization" "45c1f5e3f05d0"})
             {link-status :status
              link-response :parsed-body}
             (post (str "ctia/incident/" (-> (:id incident-body)
                                             long-id->id
                                             :short-id) "/link")
                   :body {:casebook_id (:id casebook-body)}
                   :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 201 casebook-status))
         (is (= 201 incident-status))
         (is (= 201 link-status))

         (is (= (:id casebook-body)
                (:source_ref link-response))
             "The New Relationship targets the incident")

         (is (= (:id incident-body)
                (:target_ref link-response))
             "The New Relationship sources the casebook"))))))

