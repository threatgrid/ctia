(ns ctia.http.routes.campaign-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.test-helpers
             [core :as mth]
             [http :refer [encode]]]
            [clojure
             [string :as str]
             [test :refer [is join-fixtures testing use-fixtures]]]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.properties :refer [get-http-show]]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [delete get post put]]
             [fake-whoami-service :as whoami-helpers]
             [search :refer [test-query-string-search]]
             [store :refer [deftest-for-each-store]]]
            [ctim.domain.id :as id]
            [ctim.examples.campaigns :as ex]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-campaign-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "POST /ctia/campaign"
    (let [{status :status
           campaign :parsed-body}
          (post "ctia/campaign"
                :body {:external_ids ["http://ex.tld/ctia/campaign/campaign-123"
                                      "http://ex.tld/ctia/campaign/campaign-456"]
                       :title "campaign"
                       :description "description"
                       :tlp "green"
                       :campaign_type "anything goes here"
                       :intended_effect ["Theft"]
                       :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                                    :end_time "2016-07-11T00:40:48.212-00:00"}}
                :headers {"Authorization" "45c1f5e3f05d0"})

          campaign-id (id/long-id->id (:id campaign))
          campaign-external-ids (:external_ids campaign)]
      (is (= 201 status))
      (is (deep=
           {:id (id/long-id campaign-id)
            :type "campaign"
            :external_ids ["http://ex.tld/ctia/campaign/campaign-123"
                           "http://ex.tld/ctia/campaign/campaign-456"]
            :title "campaign"
            :description "description"
            :tlp "green"
            :schema_version schema-version
            :campaign_type "anything goes here"
            :intended_effect ["Theft"]
            :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                         :end_time #inst "2016-07-11T00:40:48.212-00:00"}}
           campaign))

      (testing "the campaign ID has correct fields"
        (let [show-props (get-http-show)]
          (is (= (:hostname    campaign-id)      (:hostname    show-props)))
          (is (= (:protocol    campaign-id)      (:protocol    show-props)))
          (is (= (:port        campaign-id)      (:port        show-props)))
          (is (= (:path-prefix campaign-id) (seq (:path-prefix show-props))))))

      (testing "GET /ctia/campaign/external_id/:external_id"
        (let [response (get (format "ctia/campaign/external_id/%s"
                                    (encode (rand-nth campaign-external-ids)))
                            :headers {"Authorization" "45c1f5e3f05d0"})
              campaigns (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               [{:id (id/long-id campaign-id)
                 :type "campaign"
                 :external_ids ["http://ex.tld/ctia/campaign/campaign-123"
                                "http://ex.tld/ctia/campaign/campaign-456"]
                 :title "campaign"
                 :description "description"
                 :tlp "green"
                 :schema_version schema-version
                 :campaign_type "anything goes here"
                 :intended_effect ["Theft"]
                 :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                              :end_time #inst "2016-07-11T00:40:48.212-00:00"}}]
               campaigns))))

      (test-query-string-search :campaign "description" :description)

      (testing "GET /ctia/campaign/:id"
        (let [response (get (str "ctia/campaign/" (:short-id campaign-id))
                            :headers {"Authorization" "45c1f5e3f05d0"})
              campaign (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:id (id/long-id campaign-id)
                :type "campaign"
                :external_ids ["http://ex.tld/ctia/campaign/campaign-123"
                               "http://ex.tld/ctia/campaign/campaign-456"]
                :title "campaign"
                :description "description"
                :tlp "green"
                :schema_version schema-version
                :campaign_type "anything goes here"
                :intended_effect ["Theft"]
                :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                             :end_time #inst "2016-07-11T00:40:48.212-00:00"}}
               campaign))))

      (testing "PUT /ctia/campaign/:id"
        (let [{status :status
               updated-campaign :parsed-body}
              (put (str "ctia/campaign/" (:short-id campaign-id))
                   :body {:title "modified campaign"
                          :external_ids ["http://ex.tld/ctia/campaign/campaign-123"
                                         "http://ex.tld/ctia/campaign/campaign-456"]
                          :description "different description"
                          :tlp "amber"
                          :campaign_type "anything goes here"
                          :intended_effect ["Brand Damage"]
                          :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                                       :end_time "2016-07-11T00:40:48.212-00:00"}}
                   :headers {"Authorization" "45c1f5e3f05d0"})

              updated-campaign-id (id/long-id->id (:id updated-campaign))]
          (is (= 200 status))
          (is (deep=
               {:id (id/long-id updated-campaign-id)
                :external_ids ["http://ex.tld/ctia/campaign/campaign-123"
                               "http://ex.tld/ctia/campaign/campaign-456"]
                :type "campaign"
                :title "modified campaign"
                :description "different description"
                :tlp "amber"
                :schema_version schema-version
                :campaign_type "anything goes here"
                :intended_effect ["Brand Damage"]
                :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                             :end_time #inst "2016-07-11T00:40:48.212-00:00"}}
               updated-campaign))))

      (testing "PUT invalid /ctia/campaign/:id"
        (let [{status :status
               body :body}
              (put (str "ctia/campaign/" (:short-id campaign-id))
                   :body {;; This field has an invalid length
                          :title (apply str (repeatedly 1025 (constantly \0)))
                          :external_ids ["http://ex.tld/ctia/campaign/campaign-123"
                                         "http://ex.tld/ctia/campaign/campaign-456"]
                          :description "different description"
                          :tlp "amber"
                          :campaign_type "anything goes here"
                          :intended_effect ["Brand Damage"]
                          :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                                       :end_time "2016-07-11T00:40:48.212-00:00"}}
                   :headers {"Authorization" "45c1f5e3f05d0"})]
          (is (= status 400))
          (is (re-find #"error.*in.*title" (str/lower-case body)))))

      (testing "DELETE /ctia/campaign/:id"
        (let [response (delete (str "ctia/campaign/" (:short-id campaign-id))
                               :headers {"Authorization" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "ctia/campaign/" (:short-id campaign-id))
                              :headers {"Authorization" "45c1f5e3f05d0"})]
            (is (= 404 (:status response))))))))

  (testing "POST invalid /ctia/campaign"
    (let [{status :status
           body :body}
          (post "ctia/campaign"
                :body (assoc ex/new-campaign-minimal
                             ;; This field has an invalid length
                             :title (apply str (repeatedly 1025 (constantly \0))))
                :headers {"Authorization" "45c1f5e3f05d0"})]
      (is (= status 400))
      (is (re-find #"error.*in.*title" (str/lower-case body))))))
