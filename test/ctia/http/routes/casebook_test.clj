(ns ctia.http.routes.casebook-test
  (:refer-clojure :exclude [get])
  (:require
   [ctim.schemas.common
    :refer [ctim-schema-version]]
   [clojure.set :refer [subset?]]
   [ctim.examples.casebooks
    :refer [new-casebook-minimal
            new-casebook-maximal]]
   [ctia.schemas.sorting
    :refer [casebook-sort-fields]]
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
    [core :as helpers :refer [delete get post put patch]]
    [fake-whoami-service :as whoami-helpers]
    [pagination :refer [pagination-test]]
    [field-selection :refer [field-selection-tests]]
    [search :refer [test-query-string-search]]
    [store :refer [deftest-for-each-store]]]
   [ctim.domain.id :as id]))


(use-fixtures :once
  (join-fixtures [mth/fixture-schema-validation
                  helpers/fixture-properties:clean
                  whoami-helpers/fixture-server]))

(use-fixtures :each
  whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-casebook-routes
  (helpers/set-capabilities! "foouser"
                             ["foogroup"]
                             "user"
                             all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                      "foouser"
                                      "foogroup"
                                      "user")

  (testing "POST /ctia/casebook"
    (let [new-casebook (-> new-casebook-maximal
                           (dissoc :id)
                           (assoc :description "description"))
          {status :status
           casebook :parsed-body}
          (post "ctia/casebook"
                :body new-casebook
                :headers {"Authorization" "45c1f5e3f05d0"})
          casebook-id
          (id/long-id->id (:id casebook))

          casebook-external-ids
          (:external_ids casebook)]
      (is (= 201 status))
      (is (deep=
           (assoc new-casebook :id (id/long-id casebook-id)) casebook))

      (testing "the casebook ID has correct fields"
        (let [show-props (get-http-show)]
          (is (= (:hostname    casebook-id)      (:hostname    show-props)))
          (is (= (:protocol    casebook-id)      (:protocol    show-props)))
          (is (= (:port        casebook-id)      (:port        show-props)))
          (is (= (:path-prefix casebook-id) (seq (:path-prefix show-props))))))

      (testing "GET /ctia/casebook/:id"
        (let [response (get (str "ctia/casebook/" (:short-id casebook-id))
                            :headers {"Authorization" "45c1f5e3f05d0"})
              casebook (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               (assoc new-casebook :id (id/long-id casebook-id)) casebook))))

      (test-query-string-search :casebook "description" :description)

      (testing "GET /ctia/casebook/external_id/:external_id"
        (let [response (get (format "ctia/casebook/external_id/%s"
                                    (encode (rand-nth casebook-external-ids)))
                            :headers {"Authorization" "45c1f5e3f05d0"})
              casebooks (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               [(assoc casebook :id (id/long-id casebook-id))]
               casebooks))))

      (testing "PUT /ctia/casebook/:id"
        (let [with-updates (assoc casebook
                                  :title "modified casebook")
              response (put (str "ctia/casebook/" (:short-id casebook-id))
                            :body with-updates
                            :headers {"Authorization" "45c1f5e3f05d0"})
              updated-casebook (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               with-updates
               updated-casebook))))

      (testing "PATCH /ctia/casebook/:id"
        (let [updates {:title "patched casebook"}
              response (patch (str "ctia/casebook/" (:short-id casebook-id))
                              :body updates
                              :headers {"Authorization" "45c1f5e3f05d0"})
              updated-casebook (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= "patched casebook"
                 (:title updated-casebook)))

          (patch (str "ctia/casebook/" (:short-id casebook-id))
                 :body {:title "casebook"}
                 :headers {"Authorization" "45c1f5e3f05d0"})))
      
      ;; -------- partial update operation tests ------------

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


      (testing "DELETE /ctia/casebook/:id"
        (let [response (delete (str "ctia/casebook/" (:short-id casebook-id))
                               :headers {"Authorization" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "ctia/casebook/" (:short-id casebook-id))
                              :headers {"Authorization" "45c1f5e3f05d0"})]
            (is (= 404 (:status response))))))))


  (testing "POST invalid /ctia/casebook"
    (let [{status :status
           body :body}
          (post "ctia/casebook"
                ;; This field has an invalid length
                :body (assoc new-casebook-minimal
                             :title (clojure.string/join (repeatedly 1025 (constantly \0))))
                :headers {"Authorization" "45c1f5e3f05d0"})]
      (is (= status 400))
      (is (re-find #"error.*in.*title" (str/lower-case body))))))

(deftest-for-each-store test-casebook-pagination-field-selection
  (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                      "foouser"
                                      "foogroup"
                                      "user")

  (let [posted-docs
        (doall (map #(:parsed-body
                      (post "ctia/casebook"
                            :body (-> new-casebook-maximal
                                      (dissoc :id)
                                      (assoc :source (str "dotimes " %)
                                             :title "foo"))
                            :headers {"Authorization" "45c1f5e3f05d0"}))
                    (range 0 30)))]

    (pagination-test
     "ctia/casebook/search?query=*"
     {"Authorization" "45c1f5e3f05d0"}
     casebook-sort-fields)

    (field-selection-tests
     ["ctia/casebook/search?query=*"
      (-> posted-docs first :id doc-id->rel-url)]
     {"Authorization" "45c1f5e3f05d0"}
     casebook-sort-fields)))

(deftest-for-each-store test-casebook-routes-access-control
  (access-control-test "casebook"
                       new-casebook-minimal
                       true
                       true))
