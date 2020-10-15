(ns ctia.http.server-test
  (:require [schema.test :refer [validate-schemas]]
            [ctia.http.server :as sut]
            [ctia.test-helpers
             [core :as helpers :refer [GET with-properties]]
             [es :as es-helpers]]
            [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :once
  es-helpers/fixture-properties:es-store
  validate-schemas)

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
    (helpers/fixture-ctia-with-app
     (fn [app]
       (let [{:keys [headers]}
             (GET app
                  "ctia/version")]
         (is (nil? (get headers "Server")))))))

  (testing "Server should not be sent if disabled"
    (with-properties ["ctia.http.send-server-version" false]
      (helpers/fixture-ctia-with-app
       (fn [app]
         (let [{:keys [headers]}
               (GET app
                    "ctia/version")]
           (is (nil? (get headers "Server"))))))))

  (testing "Server should be sent if enabled"
    (with-properties ["ctia.http.send-server-version" true]
      (helpers/fixture-ctia-with-app
       (fn [app]
         (let [{:keys [headers]}
               (GET app
                    "ctia/version")]
           (is (get headers "Server"))))))))

(deftest build-csp-test
  (is (= (str "default-src 'self'; style-src 'self' 'unsafe-inline'; "
              "img-src 'self' data:; script-src 'self' 'unsafe-inline'; "
              "connect-src 'self';")
         (sut/build-csp {})))
  (is (= (str "default-src 'self'; style-src 'self' 'unsafe-inline'; "
              "img-src 'self' data:; script-src 'self' 'unsafe-inline'; "
              "connect-src 'self' https://visibility.int.iroh.site/iroh/oauth2/token;")
         (sut/build-csp
          {:swagger
           {:oauth2
            {:token-url "https://visibility.int.iroh.site/iroh/oauth2/token"}}})))
  (is (= (str "default-src 'self'; style-src 'self' 'unsafe-inline'; "
              "img-src 'self' data:; script-src 'self' 'unsafe-inline'; "
              "connect-src 'self' https://visibility.int.iroh.site/iroh/oauth2/token "
              "https://visibility.int.iroh.site/iroh/oauth2/refresh;")
         (sut/build-csp
          {:swagger
           {:oauth2
            {:token-url "https://visibility.int.iroh.site/iroh/oauth2/token"
             :refresh-url "https://visibility.int.iroh.site/iroh/oauth2/refresh"}}}))))
