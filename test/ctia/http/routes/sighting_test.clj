(ns ctia.http.routes.sighting-test
  (:refer-clojure :exclude [get])
  (:require
   [clojure.test :refer [deftest is testing use-fixtures join-fixtures]]
   [clojure.test.check.clojure-test :refer [defspec]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [schema-generators.generators :as g]
   [schema-tools.core :as st]
   [ctia.test-helpers.core :refer [delete get post put] :as helpers]
   [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
   [ctia.test-helpers.store :refer [deftest-for-each-store]]
   [ctia.test-helpers.auth :refer [all-capabilities]]
   [ctia.schemas.sighting  :refer [NewSighting
                                   StoredSighting
                                   check-new-sighting]]))

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
  (doseq [new-sighting (g/sample 1 (st/dissoc NewSighting :relations))]
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

        (let [another-new-sighting (first (g/sample 1 (st/dissoc NewSighting :relations)))]
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

;; Should use fixtures, need refactoring
;;
;; (defspec sighting-without-any-observable-or-indicator-is-rejected
;;   20
;;   (prop/for-all [new-sighting (gen/fmap
;;                                #(assoc % :observables [] :indicators [])
;;                                (g/generator NewSighting))]
;;     (= 422
;;        (:status (post "ctia/sighting"
;;                       :body new-sighting
;;                       :headers {"api_key" api-key})))))

(deftest-for-each-store ^:slow sighting-creation-without-any-observable-or-indicator-is-rejected
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response api-key "foouser" "user")
  (testing "Creation of sighting without obserable or indicator are rejected"
    (reduce (fn [_ new-sighting]
              (let [resp (post "ctia/sighting"
                               :body new-sighting
                               :headers {"api_key" api-key})
                    res (= 422 (:status resp))]
                (if res
                  (is (= 422 (:status resp)))
                  (reduced
                   (is (= 422 (:status resp)))))))
            (->> (g/sample 20 (st/dissoc NewSighting
                                         :relations))
                 (map #(assoc % :observables [] :indicators []))))))

(deftest-for-each-store ^:slow sighting-update-without-any-observable-or-indicator-is-rejected
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response api-key "foouser" "user")
  (testing "Update of sighting without obserable or indicator are rejected"
    (reduce (fn [_ new-sighting]
              (let [created-resp (post "ctia/sighting"
                                       :body new-sighting
                                       :headers {"api_key" api-key})
                    sig-id (get-in created-resp [:parsed-body :id])
                    resp (put (str "ctia/sighting/" sig-id)
                              :body (assoc (g/generate (st/dissoc NewSighting
                                                                  :relations))
                                           :observables []
                                           :indicators [])
                              :headers {"api_key" api-key})
                    res (= 422 (:status resp))]
                (when (= 200 (:status created-resp))
                  (if res
                    (is (= 422 (:status resp)))
                    (reduced
                     (is (= 422 (:status resp))))))))
            (filter check-new-sighting
                    (g/sample 25 (st/dissoc NewSighting
                                            :relations))))))
