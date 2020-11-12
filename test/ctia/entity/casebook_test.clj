(ns ctia.entity.casebook-test
  (:require
   [ctia.domain.entities :refer [schema-version]]
   [clj-momo.test-helpers.core :as mth]
   [clojure
    [set :refer [subset?]]
    [test :refer [deftest is join-fixtures testing use-fixtures]]]
   [ctia.entity.casebook :as sut]
   [ctia.test-helpers
    [access-control :refer [access-control-test]]
    [auth :refer [all-capabilities]]
    [core :as helpers :refer [POST PUT POST-entity-bulk]]
    [crud :refer [entity-crud-test]]
    [aggregate :refer [test-metric-routes]]
    [fake-whoami-service :as whoami-helpers]
    [field-selection :refer [field-selection-tests]]
    [http :refer [doc-id->rel-url]]
    [pagination :refer [pagination-test]]
    [store :refer [test-for-each-store-with-app]]]
   [ctim.examples.casebooks
    :refer
    [new-casebook-maximal new-casebook-minimal]]
   [ctim.schemas.common :refer [ctim-schema-version]]))

(defn partial-operations-tests [app casebook-id casebook]
  ;; observables
  (testing "POST /ctia/casebook/:id/observables :add"
    (let [new-observables [{:type "ip" :value "42.42.42.42"}]
          expected-entity (update casebook :observables concat new-observables)
          response (POST app
                         (str "ctia/casebook/" (:short-id casebook-id) "/observables")
                         :body {:operation :add
                                :observables new-observables}
                         :headers {"Authorization" "45c1f5e3f05d0"})
          updated-casebook (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (deep= expected-entity updated-casebook))))

  (testing "POST /ctia/casebook/:id/observables :add on non existing casebook"
    (let [new-observables [{:type "ip" :value "42.42.42.42"}]
          expected-entity (update casebook :observables concat new-observables)
          response (POST app
                         (str "ctia/casebook/" (str (:short-id casebook-id) "42") "/observables")
                         :body {:operation :add
                                :observables new-observables}
                         :headers {"Authorization" "45c1f5e3f05d0"})
          updated-casebook (:parsed-body response)]
      (is (= 404 (:status response)))))

  (testing "POST /ctia/casebook/:id/observables :remove"
    (let [deleted-observables [{:value "85:28:cb:6a:21:41" :type "mac_address"}
                               {:value "42.42.42.42" :type "ip"}]
          expected-entity (update casebook :observables #(remove (set deleted-observables) %))
          response (POST app
                         (str "ctia/casebook/" (:short-id casebook-id) "/observables")
                         :body {:operation :remove
                                :observables deleted-observables}
                         :headers {"Authorization" "45c1f5e3f05d0"})
          updated-casebook (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (deep= expected-entity
                 updated-casebook))))

  (testing "POST /ctia/casebook/:id/observables :remove on non existing casebook"
    (let [deleted-observables [{:value "85:28:cb:6a:21:41" :type "mac_address"}
                               {:value "42.42.42.42" :type "ip"}]
          expected-entity (update casebook :observables #(remove (set deleted-observables) %))
          response (POST app
                         (str "ctia/casebook/" (:short-id casebook-id) "z" "/observables")
                         :body {:operation :remove
                                :observables deleted-observables}
                         :headers {"Authorization" "45c1f5e3f05d0"})
          updated-casebook (:parsed-body response)]
      (is (= 404 (:status response)))))

  (testing "POST /ctia/casebook/:id/observables :replace"
    (let [observables [{:value "42.42.42.42" :type "ip"}]
          expected-entity (assoc casebook :observables observables)
          response (POST app
                         (str "ctia/casebook/" (:short-id casebook-id) "/observables")
                         :body {:operation :replace
                                :observables observables}
                         :headers {"Authorization" "45c1f5e3f05d0"})
          updated-casebook (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (deep= expected-entity
                 updated-casebook))))

  (testing "POST /ctia/casebook/:id/observables :replace"
    (let [observables (:observables new-casebook-maximal)
          expected-entity (assoc casebook :observables observables)
          response (POST app
                         (str "ctia/casebook/" (:short-id casebook-id) "/observables")
                         :body {:operation :replace
                                :observables observables}
                         :headers {"Authorization" "45c1f5e3f05d0"})
          updated-casebook (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (deep= expected-entity
                 updated-casebook))))

  (testing "POST /ctia/casebook/:id/observables :replace on non existing casebook"
    (let [observables (:observables new-casebook-maximal)
          expected-entity (assoc casebook :observables observables)
          response (POST app
                         (str "ctia/casebook/" (:short-id casebook-id) "z" "/observables")
                         :body {:operation :replace
                                :observables observables}
                         :headers {"Authorization" "45c1f5e3f05d0"})
          updated-casebook (:parsed-body response)]
      (is (= 404 (:status response)))))
  ;; texts
  (testing "POST /ctia/casebook/:id/texts :add"
    (let [new-texts [{:type "some" :text "text"}]
          expected-entity (update casebook :texts concat new-texts)
          response (POST app
                         (str "ctia/casebook/" (:short-id casebook-id) "/texts")
                         :body {:operation :add
                                :texts new-texts}
                         :headers {"Authorization" "45c1f5e3f05d0"})
          updated-casebook (:parsed-body response)]


      (is (= 200 (:status response)))
      (is (deep= expected-entity updated-casebook))))

  (testing "POST /ctia/casebook/:id/texts :add on non existing casebook"
    (let [new-texts [{:type "some" :text "text"}]
          expected-entity (update casebook :texts concat new-texts)
          response (POST app
                         (str "ctia/casebook/" (:short-id casebook-id) "z" "/texts")
                         :body {:operation :add
                                :texts new-texts}
                         :headers {"Authorization" "45c1f5e3f05d0"})
          updated-casebook (:parsed-body response)]
      (is (= 404 (:status response)))))

  (testing "POST /ctia/casebook/:id/texts :remove"
    (let [deleted-texts [{:type "some" :text "text"}]
          expected-entity (update casebook :texts #(remove (set deleted-texts) %))
          response (POST app
                         (str "ctia/casebook/" (:short-id casebook-id) "/texts")
                         :body {:operation :remove
                                :texts deleted-texts}
                         :headers {"Authorization" "45c1f5e3f05d0"})
          updated-casebook (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (deep= expected-entity updated-casebook))))

  (testing "POST /ctia/casebook/:id/texts :remove on non existing casebook"
    (let [deleted-texts [{:type "some" :text "text"}]
          expected-entity (update casebook :texts #(remove (set deleted-texts) %))
          response (POST app
                         (str "ctia/casebook/" (:short-id casebook-id) "z" "/texts")
                         :body {:operation :remove
                                :texts deleted-texts}
                         :headers {"Authorization" "45c1f5e3f05d0"})
          updated-casebook (:parsed-body response)]
      (is (= 404 (:status response)))))

  (testing "POST /ctia/casebook/:id/texts :replace"
    (let [texts [{:type "text" :text "text"}]
          response (POST app
                         (str "ctia/casebook/" (:short-id casebook-id) "/texts")
                         :body {:operation :replace
                                :texts texts}
                         :headers {"Authorization" "45c1f5e3f05d0"})
          updated-casebook (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= texts (:texts updated-casebook)))))

  (testing "POST /ctia/casebook/:id/texts :replace"
    (let [texts (:texts new-casebook-maximal)
          response (POST app
                         (str "ctia/casebook/" (:short-id casebook-id) "/texts")
                         :body {:operation :replace
                                :texts texts}
                         :headers {"Authorization" "45c1f5e3f05d0"})
          updated-casebook (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= texts (:texts updated-casebook)))))

  (testing "POST /ctia/casebook/:id/texts :replace on non existing casebook"
    (let [texts (:texts new-casebook-maximal)
          response (POST app
                         (str "ctia/casebook/" (:short-id casebook-id) "z" "/texts")
                         :body {:operation :replace
                                :texts texts}
                         :headers {"Authorization" "45c1f5e3f05d0"})
          updated-casebook (:parsed-body response)]
      (is (= 404 (:status response)))))

  ;; bundle
  (testing "POST /ctia/casebook/:id/bundle :add"
    (let [new-bundle-entities {:malwares #{{:id "transient:616608f4-7658-49f1-8728-d9a3dde849d5"
                                            :type "malware"
                                            :schema_version ctim-schema-version
                                            :name "TEST"
                                            :labels ["malware"]}}}
          response (POST app
                         (str "ctia/casebook/" (:short-id casebook-id) "/bundle")
                         :body {:operation :add
                                :bundle new-bundle-entities}
                         :headers {"Authorization" "45c1f5e3f05d0"})
          updated-casebook (:parsed-body response)]

      (is (= 200 (:status response)))
      (is (not= (:malwares updated-casebook)
                (:malwares new-bundle-entities)))
      (is (subset? (set (:malwares new-bundle-entities))
                   (set (-> updated-casebook :bundle :malwares))))))

  (testing "POST /ctia/casebook/:id/bundle :add on non existing casebook"
    (let [new-bundle-entities {:malwares #{{:id "transient:616608f4-7658-49f1-8728-d9a3dde849d5"
                                            :type "malware"
                                            :schema_version ctim-schema-version
                                            :name "TEST"
                                            :labels ["malware"]}}}
          response (POST app
                         (str "ctia/casebook/" (:short-id casebook-id) "z" "/bundle")
                         :body {:operation :add
                                :bundle new-bundle-entities}
                         :headers {"Authorization" "45c1f5e3f05d0"})
          updated-casebook (:parsed-body response)]
      (is (= 404 (:status response)))))

  (testing "POST /ctia/casebook/:id/bundle :remove"
    (let [deleted-bundle-entities {:malwares #{{:id "transient:616608f4-7658-49f1-8728-d9a3dde849d5"
                                                :type "malware"
                                                :schema_version ctim-schema-version
                                                :name "TEST"
                                                :labels ["malware"]}}}
          response (POST app
                         (str "ctia/casebook/" (:short-id casebook-id) "/bundle")
                         :body {:operation :remove
                                :bundle deleted-bundle-entities}
                         :headers {"Authorization" "45c1f5e3f05d0"})
          updated-casebook (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (deep= (update casebook :bundle dissoc :malwares)
                 (update updated-casebook :bundle dissoc :malwares)))
      (is (not (subset? (set (:malwares deleted-bundle-entities))
                        (set (-> updated-casebook :bundle :malwares)))))))

  (testing "POST /ctia/casebook/:id/bundle :remove on non existing casebook"
    (let [deleted-bundle-entities {:malwares #{{:id "transient:616608f4-7658-49f1-8728-d9a3dde849d5"
                                                :type "malware"
                                                :schema_version ctim-schema-version
                                                :name "TEST"
                                                :labels ["malware"]}}}
          response (POST app
                         (str "ctia/casebook/" (:short-id casebook-id) "z" "/bundle")
                         :body {:operation :remove
                                :bundle deleted-bundle-entities}
                         :headers {"Authorization" "45c1f5e3f05d0"})
          updated-casebook (:parsed-body response)]
      (is (= 404 (:status response)))))

  (testing "POST /ctia/casebook/:id/bundle :replace"
    (let [bundle-entities {:malwares #{{:id "transient:616608f4-7658-49f1-8728-d9a3dde849d5"
                                        :type "malware"
                                        :schema_version ctim-schema-version
                                        :name "TEST"
                                        :labels ["malware"]}}}
          response (POST app
                         (str "ctia/casebook/" (:short-id casebook-id) "/bundle")
                         :body {:operation :replace
                                :bundle bundle-entities}
                         :headers {"Authorization" "45c1f5e3f05d0"})
          updated-casebook (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (deep= (update casebook :bundle dissoc :malwares)
                 (update updated-casebook :bundle dissoc :malwares)))
      (is (= (:malwares bundle-entities)
             (-> updated-casebook :bundle :malwares)))))

  (testing "POST /ctia/casebook/:id/bundle :replace on non existing casebook"
    (let [bundle-entities {:malwares #{{:id "transient:616608f4-7658-49f1-8728-d9a3dde849d5"
                                        :type "malware"
                                        :schema_version ctim-schema-version
                                        :name "TEST"
                                        :labels ["malware"]}}}
          response (POST app
                         (str "ctia/casebook/" (:short-id casebook-id) "z" "/bundle")
                         :body {:operation :replace
                                :bundle bundle-entities}
                         :headers {"Authorization" "45c1f5e3f05d0"})
          updated-casebook (:parsed-body response)]
      (is (= 404 (:status response)))))

  (testing "schema_version changes with PATCH"
    (testing "Test setup: PATCH /ctia/casebook/:id redefing schema_version"
      (let [fake-schema-version "0.0.42"
            expected-entity (assoc casebook :schema_version fake-schema-version)
            response (with-redefs [schema-version fake-schema-version]
                       (PUT app
                            (str "ctia/casebook/" (:short-id casebook-id))
                            :body casebook
                            :headers {"Authorization" "45c1f5e3f05d0"}))
            updated-casebook (:parsed-body response)]
        (is (= 200 (:status response)))
        (is (deep= expected-entity updated-casebook))

        (testing "Adding an observable should work and update the schema_version"
          (testing "POST /ctia/casebook/:id/observables :add"
            (let [new-observables [{:type "ip" :value "42.42.42.43"}]
                  expected-entity (-> updated-casebook
                                      (update :observables concat new-observables)
                                      (assoc :schema_version schema-version))
                  response (POST app
                                 (str "ctia/casebook/" (:short-id casebook-id) "/observables")
                                 :body {:operation :add
                                        :observables new-observables}
                                 :headers {"Authorization" "45c1f5e3f05d0"})
                  final-casebook (:parsed-body response)]
              (is (= 200 (:status response)))
              (is (deep= expected-entity final-casebook)))))))))

(use-fixtures :once
  (join-fixtures [mth/fixture-schema-validation
                  whoami-helpers/fixture-server]))

(deftest test-casebook-routes
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app
                                "foouser"
                                ["foogroup"]
                                "user"
                                all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (entity-crud-test {:app app
                        :entity "casebook"
                        :example new-casebook-maximal
                        :headers {:Authorization "45c1f5e3f05d0"}
                        :patch-tests? true
                        :additional-tests partial-operations-tests}))))

(deftest test-casebook-pagination-field-selection
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (let [ids (POST-entity-bulk
                app
                (assoc new-casebook-maximal :title "foo")
                :casebooks
                30
                {"Authorization" "45c1f5e3f05d0"})]

       (pagination-test
        app
        "ctia/casebook/search?query=*"
        {"Authorization" "45c1f5e3f05d0"}
        sut/casebook-fields)

       (field-selection-tests
        app
        ["ctia/casebook/search?query=*"
         (doc-id->rel-url (first ids))]
        {"Authorization" "45c1f5e3f05d0"}
        sut/casebook-fields)))))

(deftest test-casebook-routes-access-control
  (access-control-test "casebook"
                       new-casebook-minimal
                       true
                       true
                       test-for-each-store-with-app))

(deftest test-casebook-metric-routes
  (test-metric-routes (into sut/casebook-entity
                            {:entity-minimal new-casebook-minimal
                             :enumerable-fields sut/casebook-enumerable-fields
                             :date-fields sut/casebook-histogram-fields})))
