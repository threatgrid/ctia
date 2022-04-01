(ns ctia.entity.attack-pattern-test
  (:require [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.entity.attack-pattern :as sut]
            [ctia.test-helpers.access-control :refer [access-control-test]]
            [ctia.test-helpers.aggregate :refer [test-metric-routes]]
            [ctia.test-helpers.auth :refer [all-capabilities]]
            [ctia.test-helpers.core :refer [GET] :as helpers]
            [ctia.test-helpers.crud :refer [entity-crud-test]]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [ctia.test-helpers.store :refer [test-for-each-store-with-app]]
            [ctim.examples.attack-patterns
             :refer
             [new-attack-pattern-maximal new-attack-pattern-minimal]]
            [schema.test :refer [validate-schemas]])
  (:import (java.net URLEncoder)))

(use-fixtures :once (join-fixtures [validate-schemas
                                    whoami-helpers/fixture-server]))

(def auth "45c1f5e3f05d0")

(defn additional-tests [app _attack-pattern-id {[{:keys [external_id url]}] :external_references :as attack-pattern}]
  (letfn [(lookup
            ([mitre-id] (lookup mitre-id auth))
            ([mitre-id auth]
             (GET app
                  (str "ctia/attack-pattern/mitre/" mitre-id)
                  :headers {"Authorization" auth})))
          (url-encode [string]
            (some-> string str (URLEncoder/encode "UTF-8") (.replace "+" "%20")))]
    (testing "GET /ctia/attack-pattern/mitre/bogus-id returns 404"
      (let [{:keys [status parsed-body] :as _resp} (lookup "bogus-id")]
        (is (= 404 status))
        (is (= "attack-pattern not found" (:error parsed-body)))))

    (testing "GET /ctia/attack-pattern/mitre/<mitre-external_id> returns the attack pattern"
      (let [{:keys [status parsed-body] :as _resp} (lookup external_id)]
        (is (= 200 status))
        (is (= attack-pattern parsed-body))))

    (testing "GET /ctia/attack-pattern/mitre/<mitre-url> returns the attack pattern"
      (let [{:keys [status parsed-body] :as _resp} (lookup (url-encode url))]
        (is (= 200 status))
        (is (= attack-pattern parsed-body))))

    (testing "GET /ctia/attack-pattern/mitre/<mitre-url> and bogus auth returns 401"
      (let [{:keys [status parsed-body] :as _resp} (lookup (url-encode url) "bogus auth")]
        (is (= 401 status))
        (is (= {:error :not_authenticated :message "Only authenticated users allowed"}
               parsed-body))))))

(deftest test-attack-pattern-crud-routes
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app
                                "foouser"
                                ["foogroup"]
                                "user"
                                all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         auth
                                         "foouser"
                                         "foogroup"
                                         "user")

     (entity-crud-test
      (into sut/attack-pattern-entity
            {:app app
             :example new-attack-pattern-maximal
             :headers {:Authorization auth}
             :update-field :title
             :invalid-test-field :title
             :additional-tests additional-tests})))))

(deftest attack-pattern-routes-access-control
  (access-control-test "attack-pattern"
                       new-attack-pattern-minimal
                       true
                       true
                       test-for-each-store-with-app))

(deftest test-attack-pattern-metric-routes
  (test-metric-routes (into sut/attack-pattern-entity
                            {:plural :attack_patterns
                             :entity-minimal new-attack-pattern-minimal
                             :enumerable-fields sut/attack-pattern-enumerable-fields
                             :date-fields sut/attack-pattern-histogram-fields})))
