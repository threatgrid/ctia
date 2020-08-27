(ns ctia.schemas.graphql.helpers-test
  (:require [ctia.schemas.graphql.helpers :as sut]
            [clj-momo.test-helpers.core :as mth]
            [clojure.test :as t :refer [deftest is testing use-fixtures]])
  (:import [graphql.schema GraphQLType]
           [java.util.concurrent CountDownLatch TimeUnit]))

(use-fixtures :once mth/fixture-schema-validation)

;; TODO when global graphql schema is stubbable, add parallel tests to new-object-test etc

(defn dummy-rt-opt []
  {:services
   {:GraphQLService
    {:get-or-update-named-type-registry
     (let [registry (atom {})]
       #(sut/get-or-update-named-type-registry registry %1 %2))}}})

(deftest new-object-test
  (testing "The same object is not created twice"
    (let [rt-opt (dummy-rt-opt)
          ;; while the graphql schema is global, we want to use the global registry
          ;; to avoid recreating objects.
          new-object-fn #(-> (sut/new-object
                               "NewObjectTestObject"
                               "Description 1"
                               []
                               {})
                             (sut/resolve-with-rt-opt rt-opt))]
      (is (identical?
            (new-object-fn)
            (new-object-fn))))))

(deftest enum-test
  (testing "The same enum is not created twice"
    (let [rt-opt (dummy-rt-opt)
          ;; while the graphql schema is global, we want to use the global registry
          ;; to avoid recreating objects.
          enum-fn #(-> (sut/enum
                         "EnumTestEnum"
                         "Description 1"
                         ["V1" "v2" "V3"])
                       (sut/resolve-with-rt-opt rt-opt))]
      (is (identical?
            (enum-fn)
            (enum-fn))))))

(deftest new-union-test
  (testing "The same union is not created twice"
    (let [rt-opt (dummy-rt-opt)
          ;; while the graphql schema is global, we want to use the global registry
          ;; to avoid recreating objects.
          union-fn #(-> (sut/new-union
                          "NewUnionTestUnion"
                          "Description"
                          (fn [obj args schema]
                            (throw (ex-info "stub" {})))
                          [(sut/new-ref "NewUnionTestRefA")
                           (sut/new-ref "NewUnionTestRefB")])
                        (sut/resolve-with-rt-opt rt-opt))]
      (is (identical?
            (union-fn)
            (union-fn))))))

(deftest get-or-update-type-named-registry-test
  (dotimes [_ 30]
    (let [rt-opt (dummy-rt-opt)
          type-name "GetOrUpdateTypeNamedRegistryTestEnum"
          ;; we simulate graphql's restriction that a named type
          ;; can only be created once, since the graphql schema is
          ;; currently global.
          runs-once (let [ran? (atom false)]
                      (fn []
                        (swap! ran? (fn [ran?]
                                      (if ran?
                                        (throw (ex-info "Redefined type!" {}))
                                        true)))
                        ;; while the graphql schema is global, we want to use the global registry
                        ;; to avoid recreating objects.
                        (-> (sut/enum
                              type-name
                              "Description 1"
                              ["V1" "v2" "V3"])
                            (sut/resolve-with-rt-opt rt-opt))))
          timeout-ms 10000
          registry (sut/create-named-type-registry)
          thread-count 5
          make-sync-fn (fn []
                         (let [latch (CountDownLatch. thread-count)]
                           #(do (.countDown latch)
                                (assert (.await latch timeout-ms TimeUnit/MILLISECONDS)))))
          sync-starts (make-sync-fn)
          threads (doall
                    (repeatedly
                      thread-count
                      #(future
                         ;; synchronize threads to increase contention
                         (sync-starts)
                         (sut/get-or-update-named-type-registry
                           registry
                           type-name
                           runs-once))))
          _ (assert (seq threads))
          expected-result (sut/get-or-update-named-type-registry
                            registry
                            type-name
                            runs-once)
          _ (assert (instance? GraphQLType expected-result))]
      (doseq [d threads
              :let [r (deref d timeout-ms :timeout)]]
        (assert (instance? GraphQLType r))
        (is (identical? expected-result r))))))

(deftest valid-type-name?-test
  (is (not (sut/valid-type-name? nil))
      "null type name is invalid")
  (is (not (sut/valid-type-name? ""))
      "empty name is invalid")
  (is (not (sut/valid-type-name? "a-b"))
      "name with dash char is invalid")
  (is (sut/valid-type-name? "judgement")
      "normal name is valid"))

(deftest valid-type-names?-test
  (is (not (sut/valid-type-names? ["a-b" "aa"]))
      "Invalid if one of the collection is not valid")
  (is (sut/valid-type-names? ["judgement" "indicator"])
      "Valid if all names are valid"))
