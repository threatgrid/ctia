(ns ctia.http.server-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.test-helpers.core :as mth]
            [ctia.test-helpers
             [core :as helpers :refer [post get with-properties]]
             [es :as es-helpers]]
            [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :once mth/fixture-schema-validation)

(use-fixtures :each
  helpers/fixture-properties:clean)

(deftest version-header-test
  (testing "Server should not be sent by default"
    (helpers/fixture-ctia
     (fn []
       (let [{:keys [headers]}
             (get "ctia/version")]
         (is (nil? (clojure.core/get headers "Server")))))))

  (testing "Server should not be sent if disabled"
    (with-properties ["ctia.http.send-server-version" false]
      (helpers/fixture-ctia
       (fn []
         (let [{:keys [headers]}
               (get "ctia/version")]
           (is (nil? (clojure.core/get headers "Server"))))))))

  (testing "Server should be sent if enabled"
    (with-properties ["ctia.http.send-server-version" true]
      (helpers/fixture-ctia
       (fn []
         (let [{:keys [headers]}
               (get "ctia/version")]
           (is (clojure.core/get headers "Server"))))))))
