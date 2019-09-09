(ns ctia.entity.judgement-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.lib.clj-time.core :as time]
            [clj-momo.test-helpers.core :as mth]
            [clj-http.fake :as fake]
            [clj-time.core :as t]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.domain.entities :refer [schema-version]]
            ctia.properties
            [ctia.entity.judgement.schemas
             :refer [judgement-fields judgement-sort-fields]]
            [ctia.test-helpers
             [access-control :refer [access-control-test]]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [get post post-entity-bulk]]
             [crud :refer [entity-crud-test]]
             [fake-whoami-service :as whoami-helpers]
             [field-selection :refer [field-selection-tests]]
             [http :refer [doc-id->rel-url]]
             [pagination :refer [pagination-test]]
             [store :refer [test-for-each-store]]]
            [ctim.domain.id :as id]
            [ctia.test-helpers.es :as es-helpers]
            [ctim.examples.judgements :as ex :refer [new-judgement-maximal]]
            [clojure.tools.logging :as log]
            [clojure.tools.logging.test :as tlog]
            ))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    helpers/fixture-properties:cors
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(def new-judgement
  (merge new-judgement-maximal
         {:observable {:value "1.2.3.4"
                       :type "ip"}
          :external_ids ["http://ex.tld/ctia/judgement/judgement-123"
                         "http://ex.tld/ctia/judgement/judgement-456"]
          :disposition 2
          :disposition_name "Malicious"
          :source "test"
          :priority 100
          :severity "High"
          :confidence "Low"
          :reason "This is a bad IP address that talked to some evil servers"}))

(defn additional-tests [judgement-id judgement]
  (testing "GET /ctia/judgement/search"
    ;; only when ES store
    (when (= "es" (get-in @ctia.properties/properties [:ctia :store :indicator]))
      (let [term "observable.value:\"1.2.3.4\""
            response (get (str "ctia/judgement/search")
                          :headers {"Authorization" "45c1f5e3f05d0"}
                          :query-params {"query" term})]
        (is (= 200 (:status response)))
        (is (= "Malicious" (first (map :disposition_name (:parsed-body response))))
            "IP quoted term works"))

      (let [term "1.2.3.4"
            response (get (str "ctia/judgement/search")
                          :headers {"Authorization" "45c1f5e3f05d0"}
                          :query-params {"query" term})]
        (is (= 200 (:status response)))
        (is (= "Malicious" (first (map :disposition_name (:parsed-body response))))
            "IP unquoted, all term works"))

      (let [term "Evil Servers"
            response (get (str "ctia/judgement/search")
                          :headers {"Authorization" "45c1f5e3f05d0"}
                          :query-params {"query" term})]
        (is (= 200 (:status response)))
        (is (= "Malicious" (first (map :disposition_name (:parsed-body response))))
            "Full text search, mixed case, _all term works"))

      (let [term "disposition_name:Malicious"
            response (get (str "ctia/judgement/search")
                          :headers {"Authorization" "45c1f5e3f05d0"}
                          :query-params {"query" term})]
        (is (= 200 (:status response)))
        (is (= "Malicious" (first (map :disposition_name (:parsed-body response))))
            "uppercase term works"))

      (let [term "disposition_name:malicious"
            response (get (str "ctia/judgement/search")
                          :headers {"Authorization" "45c1f5e3f05d0"}
                          :query-params {"query" term})]
        (is (= 200 (:status response)))
        (is (= "Malicious" (first (map :disposition_name (:parsed-body response))))
            "lowercase quoted term works"))

      (let [term "disposition_name:Malicious"
            response (get (str "ctia/judgement/search")
                          :headers {"Authorization" "45c1f5e3f05d0"}
                          :query-params {"query" term
                                         "tlp" "red"})]
        (is (= 200 (:status response)))
        (is (empty? (:parsed-body response))
            "filters are applied, and discriminate"))

      (let [term "disposition_name:Malicious"
            response (get (str "ctia/judgement/search")
                          :headers {"Authorization" "45c1f5e3f05d0"}
                          :query-params {"query" term
                                         "tlp" "green"})]
        (is (= 200 (:status response)))
        (is (= 1  (count (:parsed-body response)))
            "filters are applied, and match properly"))))

  (testing "GET /ctia/judgement/:id authentication failures"
    (testing "no Authorization"
      (let [{body :parsed-body status :status}
            (get (str "ctia/judgement/" (:short-id judgement-id)))]
        (is (= 401 status))
        (is (= {:message "Only authenticated users allowed"
                :error :not_authenticated}
               body))))

    (testing "unknown Authorization"
      (let [{body :parsed-body status :status}
            (get (str "ctia/judgement/" (:short-id judgement-id))
                 :headers {"Authorization" "1111111111111"})]
        (is (= 401 status))
        (is (= {:message "Only authenticated users allowed"
                :error :not_authenticated}
               body))))

    (testing "doesn't have read capability"
      (let [{body :parsed-body status :status}
            (get (str "ctia/judgement/" (:short-id judgement-id))
                 :headers {"Authorization" "2222222222222"})]
        (is (= 403 status))
        (is (= {:message "Missing capability",
                :capabilities :read-judgement,
                :owner "baruser"
                :error :missing_capability}
               body))))))

(deftest test-judgement-routes
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroupi"] "user" all-capabilities)
     (helpers/set-capabilities! "baruser"  ["bargroup"] "user" #{})
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (whoami-helpers/set-whoami-response "2222222222222"
                                         "baruser"
                                         "bargroup"
                                         "user")

     (entity-crud-test
      {:entity "judgement"
       :example new-judgement
       :update-tests? true
       :update-field :source
       :invalid-tests? false
       :search-tests? false
       :additional-tests additional-tests
       :headers {:Authorization "45c1f5e3f05d0"}}))))

(defn apply-fixtures
  [properties fn]
  (let [fixture-fn
        (join-fixtures [helpers/fixture-log
                        helpers/fixture-properties:clean
                        helpers/fixture-properties:cors
                        #(helpers/with-properties properties (%))
                        es-helpers/fixture-properties:es-store
                        helpers/fixture-ctia
                        es-helpers/fixture-delete-store-indexes])]
    (fixture-fn fn)))

(def new-judgement-1
  {:observable {:value "1.2.3.4"
                :type "ip"}
   :external_ids ["http://ex.tld/ctia/judgement/judgement-123"
                  "http://ex.tld/ctia/judgement/judgement-456"]
   :disposition 2
   :source "test"
   :priority 100
   :timestamp #inst "2042-01-01T00:00:00.000Z"
   :severity "High"
   :confidence "Low"
   :reason "This is a bad IP address that talked to some evil servers"
   :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}})

(deftest test-judgement-routes-for-dispositon-determination
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (testing "POST a judgement with dispositon (id)"
       (let [{status :status
              judgement :parsed-body}
             (post "ctia/judgement"
                   :body {:observable {:value "1.2.3.4"
                                       :type "ip"}
                          :disposition 2
                          :source "test"
                          :priority 100
                          :timestamp #inst "2042-01-01T00:00:00.000Z"
                          :severity "High"
                          :confidence "Low"
                          :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                   :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 201 status))
         (is (deep=
              {:type "judgement"
               :observable {:value "1.2.3.4"
                            :type "ip"}
               :disposition 2
               :disposition_name "Malicious"
               :source "test"
               :priority 100
               :timestamp #inst "2042-01-01T00:00:00.000Z"
               :severity "High"
               :confidence "Low"
               :tlp "green"
               :schema_version schema-version
               :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                            :end_time #inst "2525-01-01T00:00:00.000-00:00"}}
              (dissoc judgement
                      :id)))))

     (testing "POST a judgement with disposition_name"
       (let [{status :status
              judgement :parsed-body}
             (post "ctia/judgement"
                   :body {:observable {:value "1.2.3.4"
                                       :type "ip"}
                          :disposition_name "Malicious"
                          :source "test"
                          :priority 100
                          :timestamp #inst "2042-01-01T00:00:00.000Z"
                          :severity "High"
                          :confidence "Low"
                          :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                   :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 201 status))
         (is (deep=
              {:type "judgement"
               :observable {:value "1.2.3.4"
                            :type "ip"}
               :disposition 2
               :disposition_name "Malicious"
               :source "test"
               :priority 100
               :timestamp #inst "2042-01-01T00:00:00.000Z"
               :severity "High"
               :confidence "Low"
               :tlp "green"
               :schema_version schema-version
               :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                            :end_time #inst "2525-01-01T00:00:00.000-00:00"}}
              (dissoc judgement
                      :id)))))

     (testing "POST a judgement without disposition"
       (let [{status :status
              judgement :parsed-body}
             (post "ctia/judgement"
                   :body {:observable {:value "1.2.3.4"
                                       :type "ip"}
                          :source "test"
                          :priority 100
                          :timestamp #inst "2042-01-01T00:00:00.000Z"
                          :severity "High"
                          :confidence "Low"
                          :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                   :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 201 status))
         (is (deep=
              {:type "judgement"
               :observable {:value "1.2.3.4"
                            :type "ip"}
               :disposition 5
               :disposition_name "Unknown"
               :source "test"
               :priority 100
               :timestamp #inst "2042-01-01T00:00:00.000Z"
               :severity "High"
               :confidence "Low"
               :tlp "green"
               :schema_version schema-version
               :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                            :end_time #inst "2525-01-01T00:00:00.000-00:00"}}
              (dissoc judgement
                      :id)))))

     (testing "POST a judgement with mismatching disposition/disposition_name"
       (let [{status :status
              judgement :parsed-body}
             (post "ctia/judgement"
                   :body {:observable {:value "1.2.3.4"
                                       :type "ip"}
                          :disposition 1
                          :disposition_name "Unknown"
                          :source "test"
                          :priority 100
                          :severity "High"
                          :confidence "Low"
                          :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                   :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 400 status))
         (is (=
              {:error "Mismatching disposition and dispositon_name for judgement",
               :judgement {:observable {:value "1.2.3.4"
                                        :type "ip"}
                           :disposition 1
                           :disposition_name "Unknown"
                           :source "test"
                           :priority 100
                           :severity "High"
                           :confidence "Low"
                           :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"}}}
              judgement))))

     (testing "POST a judgement with mismatching disposition/disposition_name"
       (let [{status :status
              judgement :parsed-body}
             (post "ctia/judgement"
                   :body {:observable {:value "1.2.3.4"
                                       :type "ip"}
                          :disposition 1
                          :disposition_name "Unknown"
                          :source "test"
                          :priority 100
                          :severity "High"
                          :confidence "Low"
                          :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                   :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 400 status))
         (is (deep=
              {:error "Mismatching disposition and dispositon_name for judgement",
               :judgement {:observable {:value "1.2.3.4"
                                        :type "ip"}
                           :disposition 1
                           :disposition_name "Unknown"
                           :source "test"
                           :priority 100
                           :severity "High"
                           :confidence "Low"
                           :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"}}}
              judgement)))))))

(deftest test-judgement-pagination-field-selection
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (let [new-judgement
           (assoc new-judgement-maximal
                  :observable
                  {:value "1.2.3.4", :type "ip"})
           ids (post-entity-bulk
                new-judgement
                :judgements
                30
                {"Authorization" "45c1f5e3f05d0"})]
       (pagination-test
        "ctia/ip/1.2.3.4/judgements"
        {"Authorization" "45c1f5e3f05d0"}
        judgement-sort-fields)

       (field-selection-tests
        ["ctia/ip/1.2.3.4/judgements"
         (doc-id->rel-url (first ids))]
        {"Authorization" "45c1f5e3f05d0"}
        judgement-fields)))))

(deftest test-judgement-routes-access-control
  (test-for-each-store
   (fn []
     (access-control-test "judgement"
                          ex/new-judgement-minimal
                          true
                          true))))
