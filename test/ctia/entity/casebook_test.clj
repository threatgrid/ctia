(ns ctia.entity.casebook-test
  (:refer-clojure :exclude [get])
  (:require
   [ctia.domain.entities :refer [schema-version]]
   [clj-momo.test-helpers.core :as mth]
   [clojure
    [set :refer [subset?]]
    [test :refer [deftest is join-fixtures testing use-fixtures]]]
   [ctia.entity.casebook :refer [casebook-fields]]
   [ctia.test-helpers
    [access-control :refer [access-control-test]]
    [auth :refer [all-capabilities]]
    [core :as helpers :refer [post put patch post-entity-bulk]]
    [crud :refer [entity-crud-test]]
    [fake-whoami-service :as whoami-helpers]
    [field-selection :refer [field-selection-tests]]
    [http :refer [doc-id->rel-url]]
    [pagination :refer [pagination-test]]
    [store :refer [test-for-each-store]]]
   [ctim.examples.casebooks
    :refer
    [new-casebook-maximal new-casebook-minimal]]
   [ctim.schemas.common :refer [ctim-schema-version]]))

(defn partial-operations-tests [casebook-id casebook]
  ;; observables
  (testing "POST /ctia/casebook/:id/observables :add"
    (let [new-observables [{:type "ip" :value "42.42.42.42"}]
          expected-entity (update casebook :observables concat new-observables)
          response (post (str "ctia/casebook/" (:short-id casebook-id) "/observables")
                         :body {:operation :add
                                :observables new-observables}
                         :headers {"Authorization" "45c1f5e3f05d0"})
          updated-casebook (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (deep= expected-entity updated-casebook))))

  (testing "POST /ctia/casebook/:id/observables :remove"
    (let [deleted-observables [{:value "85:28:cb:6a:21:41" :type "mac_address"}
                               {:value "42.42.42.42" :type "ip"}]
          expected-entity (update casebook :observables #(remove (set deleted-observables) %))
          response (post (str "ctia/casebook/" (:short-id casebook-id) "/observables")
                         :body {:operation :remove
                                :observables deleted-observables}
                         :headers {"Authorization" "45c1f5e3f05d0"})
          updated-casebook (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (deep= expected-entity
                 updated-casebook))))

  (testing "POST /ctia/casebook/:id/observables :replace"
    (let [observables [{:value "42.42.42.42" :type "ip"}]
          expected-entity (assoc casebook :observables observables)
          response (post (str "ctia/casebook/" (:short-id casebook-id) "/observables")
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
          response (post (str "ctia/casebook/" (:short-id casebook-id) "/observables")
                         :body {:operation :replace
                                :observables observables}
                         :headers {"Authorization" "45c1f5e3f05d0"})
          updated-casebook (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (deep= expected-entity
                 updated-casebook))))


  ;; texts
  (testing "POST /ctia/casebook/:id/texts :add"
    (let [new-texts [{:type "some" :text "text"}]
          expected-entity (update casebook :texts concat new-texts)
          response (post (str "ctia/casebook/" (:short-id casebook-id) "/texts")
                         :body {:operation :add
                                :texts new-texts}
                         :headers {"Authorization" "45c1f5e3f05d0"})
          updated-casebook (:parsed-body response)]


      (is (= 200 (:status response)))
      (is (deep= expected-entity updated-casebook))))

  (testing "POST /ctia/casebook/:id/texts :remove"
    (let [deleted-texts [{:type "some" :text "text"}]
          expected-entity (update casebook :texts #(remove (set deleted-texts) %))
          response (post (str "ctia/casebook/" (:short-id casebook-id) "/texts")
                         :body {:operation :remove
                                :texts deleted-texts}
                         :headers {"Authorization" "45c1f5e3f05d0"})
          updated-casebook (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (deep= expected-entity updated-casebook))))

  (testing "POST /ctia/casebook/:id/texts :replace"
    (let [texts [{:type "text" :text "text"}]
          response (post (str "ctia/casebook/" (:short-id casebook-id) "/texts")
                         :body {:operation :replace
                                :texts texts}
                         :headers {"Authorization" "45c1f5e3f05d0"})
          updated-casebook (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= texts (:texts updated-casebook)))))

  (testing "POST /ctia/casebook/:id/texts :replace"
    (let [texts (:texts new-casebook-maximal)
          response (post (str "ctia/casebook/" (:short-id casebook-id) "/texts")
                         :body {:operation :replace
                                :texts texts}
                         :headers {"Authorization" "45c1f5e3f05d0"})
          updated-casebook (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (= texts (:texts updated-casebook)))))

  ;; bundle
  (testing "POST /ctia/casebook/:id/bundle :add"
    (let [new-bundle-entities {:malwares #{{:id "transient:616608f4-7658-49f1-8728-d9a3dde849d5"
                                            :type "malware"
                                            :schema_version ctim-schema-version
                                            :name "TEST"
                                            :labels ["malware"]}}}
          response (post (str "ctia/casebook/" (:short-id casebook-id) "/bundle")
                         :body {:operation :add
                                :bundle new-bundle-entities}
                         :headers {"Authorization" "45c1f5e3f05d0"})
          updated-casebook (:parsed-body response)]

      (is (= 200 (:status response)))
      (is (not= (:malwares updated-casebook)
                (:malwares new-bundle-entities)))
      (is (subset? (set (:malwares new-bundle-entities))
                   (set (-> updated-casebook :bundle :malwares))))))

  (testing "POST /ctia/casebook/:id/bundle :remove"
    (let [deleted-bundle-entities {:malwares #{{:id "transient:616608f4-7658-49f1-8728-d9a3dde849d5"
                                                :type "malware"
                                                :schema_version ctim-schema-version
                                                :name "TEST"
                                                :labels ["malware"]}}}
          response (post (str "ctia/casebook/" (:short-id casebook-id) "/bundle")
                         :body {:operation :remove
                                :bundle deleted-bundle-entities}
                         :headers {"Authorization" "45c1f5e3f05d0"})
          updated-casebook (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (deep= (update casebook :bundle dissoc :malwares)
                 (update updated-casebook :bundle dissoc :malwares)))
      (is (not (subset? (set (:malwares deleted-bundle-entities))
                        (set (-> updated-casebook :bundle :malwares)))))))

  (testing "POST /ctia/casebook/:id/bundle :replace"
    (let [bundle-entities {:malwares #{{:id "transient:616608f4-7658-49f1-8728-d9a3dde849d5"
                                        :type "malware"
                                        :schema_version ctim-schema-version
                                        :name "TEST"
                                        :labels ["malware"]}}}
          response (post (str "ctia/casebook/" (:short-id casebook-id) "/bundle")
                         :body {:operation :replace
                                :bundle bundle-entities}
                         :headers {"Authorization" "45c1f5e3f05d0"})
          updated-casebook (:parsed-body response)]
      (is (= 200 (:status response)))
      (is (deep= (update casebook :bundle dissoc :malwares)
                 (update updated-casebook :bundle dissoc :malwares)))
      (is (= (:malwares bundle-entities)
             (-> updated-casebook :bundle :malwares)))))

  (testing "schema_version changes with PATCH"
    (testing "Test setup: PATCH /ctia/casebook/:id redefing schema_version"
      (let [fake-schema-version "0.0.42"
            expected-entity (assoc casebook :schema_version fake-schema-version)
            response (with-redefs [schema-version fake-schema-version]
                       (put (str "ctia/casebook/" (:short-id casebook-id))
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
                  response (post (str "ctia/casebook/" (:short-id casebook-id) "/observables")
                                 :body {:operation :add
                                        :observables new-observables}
                                 :headers {"Authorization" "45c1f5e3f05d0"})
                  final-casebook (:parsed-body response)]
              (is (= 200 (:status response)))
              (is (deep= expected-entity final-casebook)))))))))

(use-fixtures :once
  (join-fixtures [mth/fixture-schema-validation
                  helpers/fixture-properties:clean
                  whoami-helpers/fixture-server]))

(use-fixtures :each
  whoami-helpers/fixture-reset-state)

(deftest test-casebook-routes
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser"
                                ["foogroup"]
                                "user"
                                all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (entity-crud-test {:entity "casebook"
                        :example new-casebook-maximal
                        :headers {:Authorization "45c1f5e3f05d0"}
                        :patch-tests? true
                        :additional-tests partial-operations-tests}))))

(deftest test-casebook-pagination-field-selection
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (let [ids (post-entity-bulk
                (assoc new-casebook-maximal :title "foo")
                :casebooks
                30
                {"Authorization" "45c1f5e3f05d0"})]

       (pagination-test
        "ctia/casebook/search?query=*"
        {"Authorization" "45c1f5e3f05d0"}
        casebook-fields)

       (field-selection-tests
        ["ctia/casebook/search?query=*"
         (doc-id->rel-url (first ids))]
        {"Authorization" "45c1f5e3f05d0"}
        casebook-fields)))))

(deftest test-casebook-routes-access-control
  (test-for-each-store
   (fn []
     (access-control-test "casebook"
                          new-casebook-minimal
                          true
                          true))))
