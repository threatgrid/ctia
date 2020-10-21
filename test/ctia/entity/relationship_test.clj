(ns ctia.entity.relationship-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.math.combinatorics :as comb]
            [clojure.string :as str]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.entity.relationship.schemas :as rs]
            [ctia.entity.relationship :as sut]
            [ctia.test-helpers
             [access-control :refer [access-control-test]]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [POST POST-entity-bulk]]
             [crud :refer [entity-crud-test]]
             [aggregate :refer [test-metric-routes]]
             [fake-whoami-service :as whoami-helpers]
             [field-selection :refer [field-selection-tests]]
             [http :refer [doc-id->rel-url]]
             [pagination :refer [pagination-test]]
             [store :refer [test-for-each-store-with-app]]]
            [ctim.domain.id :refer [long-id->id]]
            [ctim.examples
             [casebooks :refer [new-casebook-minimal]]
             [incidents :refer [new-incident-minimal]]
             [investigations :refer [new-investigation-minimal]]
             [relationships :refer [new-relationship-maximal new-relationship-minimal]]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    whoami-helpers/fixture-server]))

(defn establish-user! [app]
  (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
  (whoami-helpers/set-whoami-response app
                                      "45c1f5e3f05d0"
                                      "foouser"
                                      "foogroup"
                                      "user"))

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
  (test-for-each-store-with-app
   (fn [app]
     (establish-user! app)
     (testing "POST /ctia/relationship"
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
             (POST app
                   "ctia/relationship"
                   :body new-relationship
                   :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 400 status)))))))

(deftest test-relationship-routes
  (test-for-each-store-with-app
   (fn [app]
     (establish-user! app)
     (entity-crud-test
      {:app app
       :entity "relationship"
       :example new-relationship
       :headers {:Authorization "45c1f5e3f05d0"}}))))

(deftest test-relationship-pagination-field-selection
  (test-for-each-store-with-app
   (fn [app]
     (establish-user! app)
     (let [ids (POST-entity-bulk
                app
                new-relationship-maximal
                :relationships
                30
                {"Authorization" "45c1f5e3f05d0"})]
       (pagination-test
        app
        "ctia/relationship/search?query=*"
        {"Authorization" "45c1f5e3f05d0"}
        sut/relationship-fields)
       (field-selection-tests
        app
        ["ctia/relationship/search?query=*"
         (doc-id->rel-url (first ids))]
        {"Authorization" "45c1f5e3f05d0"}
        sut/relationship-fields)))))

(deftest test-relationship-routes-access-control
  (access-control-test "relationship"
                       new-relationship-minimal
                       true
                       true
                       test-for-each-store-with-app))

(deftest links-routes-test
  (test-for-each-store-with-app
   (fn [app]
     (establish-user! app)
     (testing "Indicator & Casebook test setup"
       (let [{casebook-body :parsed-body
              casebook-status :status}
             (POST app
                   "ctia/casebook"
                   :body new-casebook-minimal
                   :headers {"Authorization" "45c1f5e3f05d0"})
             {incident-body :parsed-body
              incident-status :status}
             (POST app
                   "ctia/incident"
                   :body new-incident-minimal
                   :headers {"Authorization" "45c1f5e3f05d0"})

             {wrong-incident-status :status
              wrong-incident-response :body}
             (POST app
                   (str "ctia/incident/" "r0pV4UNSjWyUYXUtpgQxooVR7HbjnMKB" "/link")
                   :body {:casebook_id (:id casebook-body)}
                   :headers {"Authorization" "45c1f5e3f05d0"})

             {wrong-casebook-status :status
              wrong-casebook-response :body}
             (POST app
                   (str "ctia/incident/" (-> (:id incident-body)
                                             long-id->id
                                             :short-id) "/link")
                   :body {:casebook_id ":"}
                   :headers {"Authorization" "45c1f5e3f05d0"})
             {link-status :status
              link-response :parsed-body}
             (POST app
                   (str "ctia/incident/" (-> (:id incident-body)
                                             long-id->id
                                             :short-id) "/link")
                   :body {:casebook_id (:id casebook-body)}
                   :headers {"Authorization" "45c1f5e3f05d0"})
             {relationship-status :status
              relationship-response :parsed-body}
             (helpers/GET app
                          (str "ctia/relationship/" (-> (:id link-response)
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
             "The New Relationship sources the casebook")

         (is (= (:id incident-body)
                (:target_ref link-response))
             "The New Relationship targets the incident")
         (is (= relationship-response
                link-response)
             "Link Response is the created relationship"))))))

(deftest incident-investigation-link-routes-test
  (test-for-each-store-with-app
   (fn [app]
     (establish-user! app)
     ;; Creates an Incident and Investigation, and tests
     ;; various (successful and unsuccessful) ways to link them
     ;; via the /incident/:id/link route.
     (testing "/incident/:id/link + :investigation_id"
       (let [{incident-body :parsed-body
              incident-status :status}
             (POST app
                   "ctia/incident"
                   :body new-incident-minimal
                   :headers {"Authorization" "45c1f5e3f05d0"})
             _ (testing "create an incident"
                 (is (= 201 incident-status)))

             {investigation-body :parsed-body
              investigation-status :status}
             (POST app
                   "ctia/investigation"
                   :body new-investigation-minimal
                   :headers {"Authorization" "45c1f5e3f05d0"})
             _ (testing "create an investigation"
                 (is (= 201 investigation-status)))

             {wrong-incident-status :status
              wrong-incident-response :body}
             (POST app
                   (str "ctia/incident/" "r0pV4UNSjWyUYXUtpgQxooVR7HbjnMKB" "/link")
                   :body {:investigation_id (:id investigation-body)}
                   :headers {"Authorization" "45c1f5e3f05d0"})
             _ (testing "cannot link non-existent incident"
                 (is (= 404 wrong-incident-status))
                 (is (= {:error "Invalid Incident id"}
                        (read-string wrong-incident-response))))

             {wrong-investigation-status :status
              wrong-investigation-response :body}
             (POST app
                   (str "ctia/incident/"
                        (-> (:id incident-body)
                            long-id->id
                            :short-id)
                        "/link")
                   :body {:investigation_id ":"}
                   :headers {"Authorization" "45c1f5e3f05d0"})
             _ (testing "cannot link non-existent investigation"
                 (is (= 400 wrong-investigation-status))
                 (is (= {:error "Invalid Investigation id"}
                        (read-string wrong-investigation-response))))

             {link-status :status
              link-response :parsed-body}
             (POST app
                   (str "ctia/incident/"
                        (-> (:id incident-body)
                            long-id->id
                            :short-id)
                        "/link")
                   :body {:investigation_id (:id investigation-body)}
                   :headers {"Authorization" "45c1f5e3f05d0"})
             _ (testing "can link existing incident and investigation"
                 (is (= 201 link-status)))

             {relationship-status :status
              relationship-response :parsed-body}
             (helpers/GET app
                          (str "ctia/relationship/"
                               (-> (:id link-response)
                                   long-id->id
                                   :short-id))
                          :headers {"Authorization" "45c1f5e3f05d0"})
             _ (testing "a relationship was created between the incident and investigation"
                 (is (= 200 relationship-status)))]

         (is (= (or (:id investigation-body)
                    ::no-investigation-id)
                (or (:source_ref link-response)
                    ::no-source-ref))
             "The new Relationship sources the investigation")

         (is (= (or (:id incident-body)
                    ::no-incident-body)
                (or (:target_ref link-response)
                    ::no-target-ref))
             "The new Relationship targets the incident")
         (is (= (or relationship-response
                    ::no-relationship-response)
                (or link-response
                    ::no-link-response))
             "Link response is the created relationship"))))))

(deftest incident-link-routes-ambiguous-test
  (test-for-each-store-with-app
   (fn [app]
     (establish-user! app)
     ;; Exactly one of a fixed set of fields is allowed in the
     ;; body of an /incident/:id/link route. This test generates
     ;; the interesting combinations of these fields that trigger 400 errors.
     (testing "/incident/:id/link + ambiguous body"
       (let [{incident-body :parsed-body
              incident-status :status}
             (POST app
                   "ctia/incident"
                   :body new-incident-minimal
                   :headers {"Authorization" "45c1f5e3f05d0"})
             _ (testing "create an incident"
                 (is (= 201 incident-status)))

             incident-short-id (-> (:id incident-body)
                                   long-id->id
                                   :short-id)
             _ (assert incident-short-id)

             one-of-kws sut/incident-link-source-types

             msg-template #(str "Please provide exactly one of the following fields: "
                                (str/join ", " (->> one-of-kws (map name) sort))
                                "\n"
                                %1)

             ;; vector of pairs "mapping" bodies that trigger '400' status to expected error messages
             body->expected-msg (into []
                                      (mapcat
                                        (fn [ss]
                                          (let [cnt (count ss)]
                                            (cond
                                              (zero? cnt) [[{} (msg-template "None provided.")]]
                                              (= 1 cnt) nil
                                              :else
                                              (let [gen-case
                                                    (fn []
                                                      ;; TODO generate from IncidentLinkRequestOptional
                                                      ;; and merge into body. then bump the number of cases
                                                      ;; generated via repeatedly.
                                                      [(zipmap ss (repeat ":"))
                                                       (msg-template
                                                         (str "Provided: "
                                                              (str/join ", " (->> ss (map name) sort))))])]
                                                (repeatedly 1 gen-case))))))
                                      (comb/subsets one-of-kws))]
         (assert (<= 2 (count body->expected-msg)))
         (doseq [[body expected-msg :as test-case] body->expected-msg]
           (let [{response :body, :keys [status]}
                 (POST app
                       (str "ctia/incident/" incident-short-id "/link")
                       :body body
                       :headers {"Authorization" "45c1f5e3f05d0"})]
             (testing test-case
               (is (= 400 status))
               (is (= {:error expected-msg}
                      (read-string response)))))))))))

(deftest test-relationship-metric-routes
  (test-metric-routes (into sut/relationship-entity
                            {:entity-minimal new-relationship-minimal
                             :enumerable-fields sut/relationship-enumerable-fields
                             :date-fields sut/relationship-histogram-fields})))
