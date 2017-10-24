(ns ctia.http.routes.investigation-test
  (:refer-clojure :exclude [get])
  (:require
   [clj-momo.test-helpers
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
    [store :refer [deftest-for-each-store]]]
   [ctim.domain.id :as id]
   [ctim.examples.investigations :as ex]))

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

  (testing "POST /ctia/attack-pattern"
    (let [{status :status
           investigation :parsed-body}
          (post "ctia/investigation"
                :body (-> ex/investigation-maximal
                          (dissoc :id)
                          (merge {:foo "foo"
                                  :bar "bar"}))
                :headers {"Authorization" "45c1f5e3f05d0"})

          investigation-id
          (id/long-id->id (:id investigation))]

      (is (= 201 status))
      (is (deep=
           (-> ex/investigation-maximal
               (dissoc :id)
               (merge {:foo "foo"
                       :bar "bar"}))
           (dissoc investigation :id)))

      (testing "the actor ID has correct fields"
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
               (-> ex/investigation-maximal
                   (dissoc :id)
                   (merge {:foo "foo"
                           :bar "bar"}))
               (dissoc investigation :id)))))

      (testing "PUT /ctia/investigation/:id"
        (let [{status :status
               updated-investigation :parsed-body}
              (put (str "ctia/investigation/" (:short-id investigation-id))
                   :body (-> ex/investigation-maximal
                             (dissoc :id)
                             (merge {:foo "spam"
                                     :bar "eggs"}))
                   :headers {"Authorization" "45c1f5e3f05d0"})]
          (is (= 200 status))
          (is (deep=
               (-> ex/investigation-maximal
                   (dissoc :id)
                   (merge {:foo "spam"
                           :bar "eggs"}))
               (dissoc updated-investigation :id)))))

      (testing "PUT /ctia/investigation/:id with invalid :title"
        (let [{status :status
               body :body}
              (put (str "ctia/investigation/" (:short-id investigation-id))
                   :body (-> ex/investigation-maximal
                             (dissoc :id)
                             (merge {:foo "foo"
                                     :bar "bar"
                                     :title (apply str
                                                   (repeatedly 1025
                                                               (constantly \0)))}))
                   :headers {"Authorization" "45c1f5e3f05d0"})]
          (is (= status 400))
          (is (re-find #"error.*in.*title" (str/lower-case body)))))

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
