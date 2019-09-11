(ns ctia.http.server-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.test-helpers.core :as mth]
            [ctia.http.server :as sut]
            [ctia.test-helpers
             [core :as helpers :refer [post get with-properties]]
             [es :as es-helpers]]
            [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :once mth/fixture-schema-validation)

(use-fixtures :each
  helpers/fixture-properties:clean)

(deftest parse-external-endpoints-test
  (is (nil? (sut/parse-external-endpoints nil)))

  (is
   (= {:msg
       (str "Wrong format for external endpoints."
            " Use 'i=url1,j=url2' where i, j are issuers."
            " Check the properties.org file of CTIA repository for some examples.")
       :data
       {:bad-string
        "IROH DEV:https://visibility.int.iroh.site/iroh/session/status,IROH TEST/https://visibility.test.iroh.site/iroh/session/status"}}
    (try
      (sut/parse-external-endpoints
       (str
        "IROH DEV:https://visibility.int.iroh.site/iroh/session/status,"
        "IROH TEST/https://visibility.test.iroh.site/iroh/session/status"))
      (catch Exception e
        {:msg (.getMessage e)
         :data (ex-data e)}))))

  (is
   (=
    {"IROH DEV" "https://visibility.int.iroh.site/iroh/session/status",
     "IROH TEST" "https://visibility.test.iroh.site/iroh/session/status"}
    (sut/parse-external-endpoints
     (str
      "IROH DEV=https://visibility.int.iroh.site/iroh/session/status,"
      "IROH TEST=https://visibility.test.iroh.site/iroh/session/status")))))

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
