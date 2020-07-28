(ns ctia.http.middleware.auth-test
  (:require [ctia.http.middleware.auth :as sut]
            [ctia.auth :as auth]
            [ctia.auth.allow-all :as auth-svc]
            [clojure.test :as t :refer [deftest is]]
            [puppetlabs.trapperkeeper.app :as app]
            [puppetlabs.trapperkeeper.testutils.bootstrap :refer [with-app-with-config]]))

(deftest add-id-to-request-test
  (is (= (sut/add-id-to-request {:status 200 :body "Test"}
                                "test-identity"
                                "test-login"
                                ["test-group"]
                                "api-key 1234-1234-1234-1234")
         {:status 200
          :body "Test"
          :identity "test-identity"
          :login "test-login"
          :groups ["test-group"]
          :headers {"authorization" "api-key 1234-1234-1234-1234"}})))

(deftest testable-wrap-authentication-test
  (with-app-with-config app
    (auth-svc/allow-all-auth-service+deps)
    {}
    (is (= {:status 200 :body "test"}
           (let [handler (fn [request] {:status 200 :body (or (:body request)
                                                              "empty body")})
                 auth-service (app/get-service app :IAuth)
                 _ (assert auth-service)
                 request {:body "test"}]
             ((sut/testable-wrap-authentication handler
                                                {:IAuth
                                                 {:identity-for-token
                                                  #(auth/identity-for-token auth-service %)}})
              request))))))
