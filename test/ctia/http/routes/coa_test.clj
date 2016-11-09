(ns ctia.http.routes.coa-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [is join-fixtures testing use-fixtures]]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.properties :refer [get-http-show]]
            [ctim.domain.id :as id]
            [ctim.schemas.common :as c]
            [ctia.test-helpers
             [search :refer [test-query-string-search]]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [delete get post put]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [deftest-for-each-store]]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-coa-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "POST /ctia/coa"
    (let [{status :status
           coa :parsed-body}
          (post "ctia/coa"
                :body {:external_ids ["http://ex.tld/ctia/coa/coa-123"
                                      "http://ex.tld/ctia/coa/coa-456"]
                       :title "coa"
                       :description "description"
                       :coa_type "Eradication"
                       :objective ["foo" "bar"]
                       :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}
                       :structured_coa_type "openc2"
                       :open_c2_coa {:type "structured_coa"
                                     :id "openc2_coa_1"
                                     :action {:type "deny"}
                                     :target {:type "cybox:Network_Connection"
                                              :specifiers "10.10.1.0"}
                                     :actuator {:type "network"
                                                :specifiers ["router"]}
                                     :modifiers {:method ["acl"]
                                                 :location "perimeter"}}}
                :headers {"api_key" "45c1f5e3f05d0"})

          coa-id (id/long-id->id (:id coa))
          coa-external-ids (:external_ids coa)]
      (is (= 201 status))
      (is (deep=
           {:id (id/long-id coa-id)
            :external_ids ["http://ex.tld/ctia/coa/coa-123"
                           "http://ex.tld/ctia/coa/coa-456"]
            :type "coa"
            :title "coa"
            :description "description"
            :tlp "green"
            :schema_version schema-version
            :coa_type "Eradication"
            :objective ["foo" "bar"]
            :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                         :end_time #inst "2525-01-01T00:00:00.000-00:00"}
            :structured_coa_type "openc2"
            :open_c2_coa {:type "structured_coa"
                          :id "openc2_coa_1"
                          :action {:type "deny"}
                          :target {:type "cybox:Network_Connection"
                                   :specifiers "10.10.1.0"}
                          :actuator {:type "network"
                                     :specifiers ["router"]}
                          :modifiers {:method ["acl"]
                                      :location "perimeter"}}
            :owner "foouser"}
           (dissoc coa
                   :created
                   :modified)))

      (testing "the coa ID has correct fields"
        (let [show-props (get-http-show)]
          (is (= (:hostname    coa-id)      (:hostname    show-props)))
          (is (= (:protocol    coa-id)      (:protocol    show-props)))
          (is (= (:port        coa-id)      (:port        show-props)))
          (is (= (:path-prefix coa-id) (seq (:path-prefix show-props))))))

      (testing "GET /ctia/coa/external_id"
        (let [response (get "ctia/coa/external_id"
                            :headers {"api_key" "45c1f5e3f05d0"}
                            :query-params {"external_id" (rand-nth coa-external-ids)})
              coas (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               [{:id (id/long-id coa-id)
                 :external_ids ["http://ex.tld/ctia/coa/coa-123"
                                "http://ex.tld/ctia/coa/coa-456"]
                 :type "coa"
                 :title "coa"
                 :description "description"
                 :tlp "green"
                 :schema_version schema-version
                 :coa_type "Eradication"
                 :objective ["foo" "bar"]
                 :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                              :end_time #inst "2525-01-01T00:00:00.000-00:00"}
                 :structured_coa_type "openc2"
                 :open_c2_coa {:type "structured_coa"
                               :id "openc2_coa_1"
                               :action {:type "deny"}
                               :target {:type "cybox:Network_Connection"
                                        :specifiers "10.10.1.0"}
                               :actuator {:type "network"
                                          :specifiers ["router"]}
                               :modifiers {:method ["acl"]
                                           :location "perimeter"}}
                 :owner "foouser"}]
               (map #(dissoc % :created :modified) coas)))))

      ;; CLB: FIXME
      ;;(test-query-string-search :coa "description:description" :description)
      
      (testing "GET /ctia/coa/:id"
        (let [response (get (str "ctia/coa/" (:short-id coa-id))
                            :headers {"api_key" "45c1f5e3f05d0"})
              coa (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:id (id/long-id coa-id)
                :external_ids ["http://ex.tld/ctia/coa/coa-123"
                               "http://ex.tld/ctia/coa/coa-456"]
                :type "coa"
                :title "coa"
                :description "description"
                :tlp "green"
                :schema_version schema-version
                :coa_type "Eradication"
                :objective ["foo" "bar"]
                :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                             :end_time #inst "2525-01-01T00:00:00.000-00:00"}
                :structured_coa_type "openc2"
                :open_c2_coa {:type "structured_coa"
                              :id "openc2_coa_1"
                              :action {:type "deny"}
                              :target {:type "cybox:Network_Connection"
                                       :specifiers "10.10.1.0"}
                              :actuator {:type "network"
                                         :specifiers ["router"]}
                              :modifiers {:method ["acl"]
                                          :location "perimeter"}}
                :owner "foouser"}
               (dissoc coa
                       :created
                       :modified)))))

      (testing "PUT /ctia/coa/:id"
        (let [{updated-coa :parsed-body
               status :status}
              (put (str "ctia/coa/" (:short-id coa-id))
                   :body {:external_ids ["http://ex.tld/ctia/coa/coa-123"
                                         "http://ex.tld/ctia/coa/coa-456"]
                          :title "updated coa"
                          :description "updated description"
                          :tlp "white"
                          :coa_type "Hardening"
                          :objective ["foo" "bar"]
                          :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}
                          :structured_coa_type "openc2"
                          :open_c2_coa {:type "structured_coa"
                                        :id "openc2_coa_1"
                                        :action {:type "allow"}
                                        :target {:type "cybox:Network_Connection"
                                                 :specifiers "10.10.1.0"}
                                        :actuator {:type "network"
                                                   :specifiers ["router"]}
                                        :modifiers {:method ["acl"]
                                                    :location "perimeter"}}}
                   :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 200 status))
          (is (deep=
               {:id (id/long-id coa-id)
                :external_ids ["http://ex.tld/ctia/coa/coa-123"
                               "http://ex.tld/ctia/coa/coa-456"]
                :type "coa"
                :created (:created coa)
                :title "updated coa"
                :description "updated description"
                :tlp "white"
                :schema_version schema-version
                :coa_type "Hardening"
                :objective ["foo" "bar"]
                :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                             :end_time #inst "2525-01-01T00:00:00.000-00:00"}
                :structured_coa_type "openc2"
                :open_c2_coa {:type "structured_coa"
                              :id "openc2_coa_1"
                              :action {:type "allow"}
                              :target {:type "cybox:Network_Connection"
                                       :specifiers "10.10.1.0"}
                              :actuator {:type "network"
                                         :specifiers ["router"]}
                              :modifiers {:method ["acl"]
                                          :location "perimeter"}}
                :owner "foouser"}
               (dissoc updated-coa
                       :modified)))))

      (testing "DELETE /ctia/coa/:id"
        (let [response (delete (str "/ctia/coa/" (:short-id coa-id))
                               :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "/ctia/coa/" (:short-id coa-id))
                              :headers {"api_key" "45c1f5e3f05d0"})]
            (is (= 404 (:status response)))))))))
