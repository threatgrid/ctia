(ns ctia.entity.relationship-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.entity.relationship :refer [relationship-fields]]
            [ctia.test-helpers
             [access-control :refer [access-control-test]]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [get post post-entity-bulk]]
             [crud :refer [entity-crud-test]]
             [fake-whoami-service :as whoami-helpers]
             [field-selection :refer [field-selection-tests]]
             [http :refer [doc-id->rel-url]]
             [pagination :refer [pagination-test]]
             [store :refer [test-for-each-store]]]
            [ctim.domain.id :refer [long-id->id]]
            [ctim.examples
             [casebooks :refer [new-casebook-minimal]]
             [incidents :refer [new-incident-minimal]]
             [relationships :refer [new-relationship-maximal new-relationship-minimal]]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(def new-relationship
  (-> new-relationship-maximal
      (assoc
       :source_ref (str "http://example.com/ctia/judgement/judgement-"
                        "f9832ac2-ee90-4e18-9ce6-0c4e4ff61a7a")
       :target_ref (str "http://example.com/ctia/indicator/indicator-"
                        "8c94ca8d-fb2b-4556-8517-8e6923d8d3c7")
       :external_ids
       ["http://ex.tld/ctia/relationship/relationship-123"
        "http://ex.tld/ctia/relationship/relationship-456"])
      (dissoc :id)))

(deftest test-relationship-routes-bad-reference
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (testing "POST /cita/relationship"
       (let [new-relationship
             (-> new-relationship-maximal
                 (assoc
                  :source_ref "http://example.com/"
                  :target_ref "http://example.com/"
                  :external_ids
                  ["http://ex.tld/ctia/relationship/relationship-123"
                   "http://ex.tld/ctia/relationship/relationship-456"])
                 (dissoc :id))
             {status :status
              {error :error} :parsed-body}
             (post "ctia/relationship"
                   :body new-relationship
                   :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 400 status)))))))

(deftest test-relationship-routes
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (entity-crud-test
      {:entity "relationship"
       :example new-relationship
       :headers {:Authorization "45c1f5e3f05d0"}}))))

(deftest test-relationship-pagination-field-selection
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (let [ids (post-entity-bulk
                new-relationship-maximal
                :relationships
                30
                {"Authorization" "45c1f5e3f05d0"})]
       (pagination-test
        "ctia/relationship/search?query=*"
        {"Authorization" "45c1f5e3f05d0"}
        relationship-fields)
       (field-selection-tests
        ["ctia/relationship/search?query=*"
         (doc-id->rel-url (first ids))]
        {"Authorization" "45c1f5e3f05d0"}
        relationship-fields)))))

(deftest test-relationship-routes-access-control
  (test-for-each-store
   (fn []
     (access-control-test "relationship"
                          new-relationship-minimal
                          true
                          true))))

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

             {wrong-incident-status :status
              wrong-incident-response :body}
             (post (str "ctia/incident/" "r0pV4UNSjWyUYXUtpgQxooVR7HbjnMKB" "/link")
                   :body {:casebook_id (:id casebook-body)}
                   :headers {"Authorization" "45c1f5e3f05d0"})

             {wrong-casebook-status :status
              wrong-casebook-response :body}
             (post (str "ctia/incident/" (-> (:id incident-body)
                                             long-id->id
                                             :short-id) "/link")
                   :body {:casebook_id ":"}
                   :headers {"Authorization" "45c1f5e3f05d0"})
             {link-status :status
              link-response :parsed-body}
             (post (str "ctia/incident/" (-> (:id incident-body)
                                             long-id->id
                                             :short-id) "/link")
                   :body {:casebook_id (:id casebook-body)}
                   :headers {"Authorization" "45c1f5e3f05d0"})
             {relationship-status :status
              relationship-response :parsed-body}
             (get (str "ctia/relationship/" (-> (:id link-response)
                                                long-id->id
                                                :short-id))
                  :headers {"Authorization" "45c1f5e3f05d0"})]

         (is (= 404 wrong-incident-status))
         (is (= {:error "Invalid Incident id"}
                (read-string wrong-incident-response)))

         (is (= 400 wrong-casebook-status))
         (is (= {:error "Invalid Casebook id"}
                (read-string wrong-casebook-response)))

         (is (= 201 casebook-status))
         (is (= 201 incident-status))
         (is (= 201 link-status))
         (is (= 200 relationship-status))

         (is (= (:id casebook-body)
                (:source_ref link-response))
             "The New Relationship targets the incident")

         (is (= (:id incident-body)
                (:target_ref link-response))
             "The New Relationship sources the casebook")
         (is (= relationship-response
                link-response)
             "Link Response is the created relationship"))))))
