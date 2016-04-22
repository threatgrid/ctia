(ns ctia.http.routes.sighting-test
  (:refer-clojure :exclude [get])
  (:require
   [clojure.test :refer [deftest is testing use-fixtures join-fixtures]]
   [schema-generators.generators :as g]
   [ctia.test-helpers.core :refer [delete get post put] :as helpers]
   [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
   [ctia.test-helpers.store :refer [deftest-for-each-store]]
   [ctia.test-helpers.auth :refer [all-capabilities]]
   [ctia.schemas.sighting  :refer [NewSighting
                                   StoredSighting]]))

(use-fixtures :once (join-fixtures [helpers/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-sighting-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "POST /ctia/sighting"
    (let [{status :status
           sighting :parsed-body
           :as response}
          (post "ctia/sighting"
                :body {:timestamp "2016-05-11T00:40:48.212-00:00"
                       :source "source"
                       :reference "http://example.com/123"
                       :confidence "High"
                       :description "description"
                       :related_judgements [{:judgement_id "judgement-123"}
                                            {:judgement_id "judgement-234"}]}
                :headers {"api_key" "45c1f5e3f05d0"})]
      (is (= 200 status))
      (is (deep=
           {:type "sighting"
            :timestamp #inst "2016-05-11T00:40:48.212-00:00"
            :source "source"
            :reference "http://example.com/123"
            :confidence "High"
            :description "description"
            :related_judgements [{:judgement_id "judgement-123"}
                                 {:judgement_id "judgement-234"}]
            :owner "foouser"}
           (dissoc sighting
                   :id
                   :created
                   :modified)))

      (testing "GET /ctia/sighting/:id"
        (let [{status :status
               sighting :parsed-body}
              (get (str "ctia/sighting/" (:id sighting))
                   :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 200 status))
          (is (deep=
               {:type "sighting"
                :timestamp #inst "2016-05-11T00:40:48.212-00:00"
                :source "source"
                :reference "http://example.com/123"
                :confidence "High"
                :description "description"
                :related_judgements [{:judgement_id "judgement-123"}
                                     {:judgement_id "judgement-234"}]
                :owner "foouser"}
               (dissoc sighting
                       :id
                       :created
                       :modified)))))

      (testing "PUT /ctia/sighting/:id"
        (let [{status :status
               updated-sighting :parsed-body}
              (put (str "ctia/sighting/" (:id sighting))
                   :body {:timestamp "2016-05-11T00:40:48.212-00:00"
                          :source "updated source"
                          :reference "http://example.com/123"
                          :confidence "Medium"
                          :description "updated description"
                          :related_judgements [{:judgement_id "judgement-123"}
                                               {:judgement_id "judgement-234"}]}
                   :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 200 status))
          (is (deep=
               {:type "sighting"
                :timestamp #inst "2016-05-11T00:40:48.212-00:00"
                :source "updated source"
                :reference "http://example.com/123"
                :confidence "Medium"
                :description "updated description"
                :related_judgements [{:judgement_id "judgement-123"}
                                     {:judgement_id "judgement-234"}]
                :owner "foouser"}
               (dissoc updated-sighting
                       :id
                       :created
                       :modified)))))

      (testing "DELETE /ctia/sighting/:id"
        (let [{status :status} (delete (str "ctia/sighting/" (:id sighting))
                                       :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 204 status))
          (let [{status :status} (get (str "ctia/sighting/" (:id sighting))
                                      :headers {"api_key" "45c1f5e3f05d0"})]
            (is (= 404 status))))))))

(deftest-for-each-store test-sighting-routes-generative
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (let [new-sightings (g/sample 20 NewSighting)]
    (testing "POST /ctia/sighting GET /ctia/sighting"
      (let [responses (map #(post "ctia/sighting"
                                  :body %
                                  :headers {"api_key" "45c1f5e3f05d0"}) new-sightings)]
        (doall (map #(is (= 200 (:status %))) responses))
        (is (deep=
             (set new-sightings)
             (->> responses
                  (map :parsed-body)
                  (map #(get (str "ctia/sighting/" (:id %))
                             :headers {"api_key" "45c1f5e3f05d0"}))
                  (map :parsed-body)
                  (map #(dissoc % :id :created :modified :owner))
                  set)))))))
