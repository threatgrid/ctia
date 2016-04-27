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

(def api-key "45c1f5e3f05d0")

(defn new-from-stored [m]
  (dissoc m :id :created :modified :owner))

(deftest-for-each-store test-sighting-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response api-key "foouser" "user")
  (doseq [new-sighting (g/sample 1 NewSighting)]
    (testing "POST /ctia/sighting"
      (let [{status :status
             sighting :parsed-body
             :as response}
            (post "ctia/sighting"
                  :body new-sighting
                  :headers {"api_key" api-key})]
        (is (= 200 status))
        (is (= new-sighting (new-from-stored sighting)))

        (testing "GET /ctia/sighting/:id"
          (let [{status :status
                 sighting :parsed-body}
                (get (str "ctia/sighting/" (:id sighting))
                     :headers {"api_key" api-key})]
            (is (= 200 status))
            (is (= new-sighting (new-from-stored sighting)))))

        (let [another-new-sighting (first (g/sample 1 NewSighting))]
          (testing "PUT /ctia/sighting/:id"
            (let [{status :status
                   updated-sighting :parsed-body}
                  (put (str "ctia/sighting/" (:id sighting))
                       :body another-new-sighting
                       :headers {"api_key" api-key})]
              (is (= 200 status))
              (is (deep=
                   another-new-sighting
                   (new-from-stored updated-sighting))))))

        (testing "DELETE /ctia/sighting/:id"
          (let [{status :status} (delete (str "ctia/sighting/" (:id sighting))
                                         :headers {"api_key" api-key})]
            (is (= 204 status))
            (let [{status :status} (get (str "ctia/sighting/" (:id sighting))
                                        :headers {"api_key" api-key})]
              (is (= 404 status)))))))))

