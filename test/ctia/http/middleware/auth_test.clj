(ns ctia.http.middleware.auth-test
  (:require [ctia.http.middleware.auth :as sut]
            [ctia.auth.allow-all :as auth-svc]
            [clojure.test :as t :refer [deftest is]]))

(deftest add-id-to-request-test
  (is (= (sut/add-id-to-request {:status 200 :body "Test"}
                                "test-identity"
                                "test-login"
                                "test-group"
                                "api-key 1234-1234-1234-1234")
         {:status 200
          :body "Test"
          :identity "test-identity"
          :login "test-login"
          :group "test-group"
          :headers {"authorization" "api-key 1234-1234-1234-1234"}})))

(deftest testable-wrap-authentication-test
  (is (= {:status 200 :body "test"}
         (let [handler (fn [request] {:status 200 :body (or (:body request)
                                                            "empty body")})
               auth-service (auth-svc/->AuthService)
               request {:body "test"}]
           ((sut/testable-wrap-authentication handler auth-service)
            request)))))
