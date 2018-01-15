(ns ctia.http.routes.investigation-test
  (:refer-clojure :exclude [get])
  (:require
   [ctim.examples.investigations
    :refer [new-investigation-minimal
            new-investigation-maximal
            investigation-maximal]]
   [ctia.schemas.sorting
    :refer [investigation-sort-fields]]
   [clj-momo.test-helpers
    [core :as mth]
    [http :refer [encode]]]
   [clojure
    [string :as str]
    [test :refer [is join-fixtures testing use-fixtures]]]
   [ctia.domain.entities :refer [schema-version]]
   [ctia.properties :refer [get-http-show]]
   [ctia.test-helpers
    [http :refer [doc-id->rel-url]]
    [access-control :refer [access-control-test]]
    [auth :refer [all-capabilities]]
    [core :as helpers :refer [delete get post put]]
    [fake-whoami-service :as whoami-helpers]
    [pagination :refer [pagination-test]]
    [field-selection :refer [field-selection-tests]]
    [search :refer [test-query-string-search]]
    [store :refer [deftest-for-each-store]]]
   [ctim.domain.id :as id]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each
  whoami-helpers/fixture-reset-state)


(deftest-for-each-store test-investigation-routes
  (helpers/set-capabilities! "foouser"
                             ["foogroup"]
                             "user"
                             all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                      "foouser"
                                      "foogroup"
                                      "user")

  (testing "POST /ctia/investigation"
    (let [{status :status
           investigation :parsed-body}
          (post "ctia/investigation"
                :body (-> investigation-maximal
                          (dissoc :id)
                          (merge {:foo "foo"
                                  :bar "bar"}))
                :headers {"Authorization" "45c1f5e3f05d0"})

          investigation-id
          (id/long-id->id (:id investigation))]

      (is (= 201 status))
      (is (deep=
           (-> investigation-maximal
               (dissoc :id)
               (merge {:foo "foo"
                       :bar "bar"}))
           (dissoc investigation :id)))

      (testing "the Investigation ID has correct fields"
        (let [show-props (get-http-show)]
          (is (= (:hostname    investigation-id)      (:hostname    show-props)))
          (is (= (:protocol    investigation-id)      (:protocol    show-props)))
          (is (= (:port        investigation-id)      (:port        show-props)))
          (is (= (:path-prefix investigation-id) (seq (:path-prefix show-props))))))

      (testing "GET /ctia/investigation/:id"
        (let [{status :status
               investigation :parsed-body}
              (get (str "ctia/investigation/" (:short-id investigation-id))
                   :headers {"Authorization" "45c1f5e3f05d0"})]
          (is (= 200 status))
          (is (deep=
               (-> investigation-maximal
                   (dissoc :id)
                   (merge {:foo "foo"
                           :bar "bar"}))
               (dissoc investigation :id)))))

      (test-query-string-search :investigation "description" :description)

      (testing "GET /ctia/investigation/external_id/:external_id"
        (let [ext-id (-> investigation-maximal :external_ids first)

              {status :status
               investigations :parsed-body}
              (get (str "ctia/investigation/external_id/" (encode ext-id))
                   :headers {"Authorization" "45c1f5e3f05d0"})]
          (is (= 200 status))
          (is (deep=
               [(-> investigation-maximal
                    (dissoc :id)
                    (merge {:foo "foo"
                            :bar "bar"}))]
               (map #(dissoc % :id) investigations)))))

      (testing "DELETE /ctia/investigation/:id"
        (let [{status :status
               :as response}
              (delete (str "ctia/investigation/" (:short-id investigation-id))
                      :headers {"Authorization" "45c1f5e3f05d0"})]
          (is (= 204 status))
          (let [{status :status}
                (get (str "ctia/investigation/" (:short-id investigation-id))
                     :headers {"Authorization" "45c1f5e3f05d0"})]
            (is (= 404 status))))))))

(deftest-for-each-store test-investigation-pagination-field-selection
  (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                      "foouser"
                                      "foogroup"
                                      "user")

  (let [posted-docs
        (doall (map #(:parsed-body
                      (post "ctia/investigation"
                            :body (-> new-investigation-maximal
                                      (dissoc :id)
                                      (assoc :source (str "dotimes " %)))
                            :headers {"Authorization" "45c1f5e3f05d0"}))
                    (range 0 30)))]

    (pagination-test
     "ctia/investigation/search?query=*"
     {"Authorization" "45c1f5e3f05d0"}
     investigation-sort-fields)

    (field-selection-tests
     ["ctia/investigation/search?query=*"
      (-> posted-docs first :id doc-id->rel-url)]
     {"Authorization" "45c1f5e3f05d0"}
     investigation-sort-fields)))

(deftest-for-each-store test-investigation-routes-access-control
  (access-control-test "investigation"
                       new-investigation-minimal
                       false
                       true))
