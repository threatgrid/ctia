(ns ctia.entity.casebook-test
  (:require
   [ctia.domain.entities :refer [schema-version]]
   [clojure
    [set :refer [subset?]]
    [test :refer [deftest is testing use-fixtures]]]
   [schema.test :refer [validate-schemas]]
   [ctia.entity.casebook :as sut]
   [ctia.test-helpers
    [access-control :refer [access-control-test]]
    [auth :refer [all-capabilities]]
    [core :as helpers :refer [POST PUT]]
    [crud :refer [entity-crud-test]]
    [aggregate :refer [test-metric-routes]]
    [fake-whoami-service :as whoami-helpers]
    [store :refer [test-for-each-store-with-app]]]
   [ctim.examples.incidents :refer [incident-maximal]]
   [ctim.examples.casebooks
    :refer
    [new-casebook-maximal new-casebook-minimal]]
   [ctim.schemas.common :refer [ctim-schema-version]]))

(use-fixtures :each
  validate-schemas
  whoami-helpers/fixture-server)

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
      (is (= (dissoc expected-entity :modified)
             (dissoc updated-casebook :modified)))))

  (testing "POST /ctia/casebook/:id/observables :add on non existing casebook"
    (let [new-observables [{:type "ip" :value "42.42.42.42"}]
          response (POST app
                         (str "ctia/casebook/" (str (:short-id casebook-id) "42") "/observables")
                         :body {:operation :add
                                :observables new-observables}
                         :headers {"Authorization" "45c1f5e3f05d0"})]
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
      (is (= (dissoc expected-entity :modified)
             (dissoc updated-casebook :modified)))))

  (testing "POST /ctia/casebook/:id/observables :remove on non existing casebook"
    (let [deleted-observables [{:value "85:28:cb:6a:21:41" :type "mac_address"}
                               {:value "42.42.42.42" :type "ip"}]
          response (POST app
                         (str "ctia/casebook/" (:short-id casebook-id) "z" "/observables")
                         :body {:operation :remove
                                :observables deleted-observables}
                         :headers {"Authorization" "45c1f5e3f05d0"})]
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
      (is (= (dissoc expected-entity :modified)
             (dissoc updated-casebook :modified)))))

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
      (is (= (dissoc expected-entity :modified)
             (dissoc updated-casebook :modified)))))

  (testing "POST /ctia/casebook/:id/observables :replace on non existing casebook"
    (let [observables (:observables new-casebook-maximal)
          response (POST app
                         (str "ctia/casebook/" (:short-id casebook-id) "z" "/observables")
                         :body {:operation :replace
                                :observables observables}
                         :headers {"Authorization" "45c1f5e3f05d0"})]
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
      (is (= (dissoc expected-entity :modified)
             (dissoc updated-casebook :modified)))))

  (testing "POST /ctia/casebook/:id/texts :add on non existing casebook"
    (let [new-texts [{:type "some" :text "text"}]
          response (POST app
                         (str "ctia/casebook/" (:short-id casebook-id) "z" "/texts")
                         :body {:operation :add
                                :texts new-texts}
                         :headers {"Authorization" "45c1f5e3f05d0"})]
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
      (is (= (dissoc expected-entity :modified)
             (dissoc updated-casebook :modified)))))

  (testing "POST /ctia/casebook/:id/texts :remove on non existing casebook"
    (let [deleted-texts [{:type "some" :text "text"}]
          response (POST app
                         (str "ctia/casebook/" (:short-id casebook-id) "z" "/texts")
                         :body {:operation :remove
                                :texts deleted-texts}
                         :headers {"Authorization" "45c1f5e3f05d0"})]
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
                         :headers {"Authorization" "45c1f5e3f05d0"})]
      (is (= 404 (:status response)))))

  ;; bundle
  (let [bundle-entities {:malwares #{{:id "transient:616608f4-7658-49f1-8728-d9a3dde849d5"
                                            :type "malware"
                                            :schema_version ctim-schema-version
                                            :title "TEST"
                                            :description "description"
                                            :short_description "short_description"
                                            :labels ["malware"]}}}]
    (testing "POST /ctia/casebook/:id/bundle :add"
      (let [response (POST app
                           (str "ctia/casebook/" (:short-id casebook-id) "/bundle")
                           :body {:operation :add
                                  :bundle bundle-entities}
                           :headers {"Authorization" "45c1f5e3f05d0"})
            updated-casebook (:parsed-body response)]

        (is (= 200 (:status response)))
        (is (not= (:malwares updated-casebook)
                  (:malwares bundle-entities)))
        (is (subset? (set (:malwares bundle-entities))
                     (set (-> updated-casebook :bundle :malwares))))))

    (testing "POST /ctia/casebook/:id/bundle :add on non existing casebook"
      (let [response (POST app
                           (str "ctia/casebook/" (:short-id casebook-id) "z" "/bundle")
                           :body {:operation :add
                                  :bundle bundle-entities}
                           :headers {"Authorization" "45c1f5e3f05d0"})]
        (is (= 404 (:status response)))))

    (testing "POST /ctia/casebook/:id/bundle :remove"
      (let [response (POST app
                           (str "ctia/casebook/" (:short-id casebook-id) "/bundle")
                           :body {:operation :remove
                                  :bundle bundle-entities}
                           :headers {"Authorization" "45c1f5e3f05d0"})
            updated-casebook (:parsed-body response)]
        (is (= 200 (:status response)))
        (is (= (-> (update casebook :bundle dissoc :malwares)
                   (dissoc :modified))
               (-> (update updated-casebook :bundle dissoc :malwares)
                   (dissoc :modified))))
        (is (not (subset? (set (:malwares bundle-entities))
                          (set (-> updated-casebook :bundle :malwares)))))))

    (testing "POST /ctia/casebook/:id/bundle :remove on non existing casebook"
      (let [response (POST app
                           (str "ctia/casebook/" (:short-id casebook-id) "z" "/bundle")
                           :body {:operation :remove
                                  :bundle bundle-entities}
                           :headers {"Authorization" "45c1f5e3f05d0"})]
        (is (= 404 (:status response)))))

    (testing "POST /ctia/casebook/:id/bundle :replace"
      (let [response (POST app
                           (str "ctia/casebook/" (:short-id casebook-id) "/bundle")
                           :body {:operation :replace
                                  :bundle bundle-entities}
                           :headers {"Authorization" "45c1f5e3f05d0"})
            updated-casebook (:parsed-body response)]
        (is (= 200 (:status response)))
        (is (= (-> (update casebook :bundle dissoc :malwares)
                   (dissoc :modified))
               (-> (update updated-casebook :bundle dissoc :malwares)
                   (dissoc :modified))))
        (is (= (:malwares bundle-entities)
               (-> updated-casebook :bundle :malwares set)))))

    (testing "POST /ctia/casebook/:id/bundle :replace on non existing casebook"
      (let [response (POST app
                           (str "ctia/casebook/" (:short-id casebook-id) "z" "/bundle")
                           :body {:operation :replace
                                  :bundle bundle-entities}
                           :headers {"Authorization" "45c1f5e3f05d0"})]
        (is (= 404 (:status response))))))

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
        (is (= (dissoc expected-entity :modified)
               (dissoc updated-casebook :modified)))

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

              (is (= (dissoc expected-entity :modified)
                     (dissoc final-casebook :modified))))))))))

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

     (entity-crud-test
      (into sut/casebook-entity
            {:app app
             :example (assoc-in new-casebook-maximal
                                [:bundle :incidents]
                                [(assoc incident-maximal :meta {:ai-description true})])
             :headers {:Authorization "45c1f5e3f05d0"}
             :patch-tests? true
             :additional-tests partial-operations-tests})))))

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
