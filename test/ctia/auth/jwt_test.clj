(ns ctia.auth.jwt-test
  (:require [ctia.auth.jwt :as sut]
            [clojure.test :as t :refer [deftest is]]))

(deftest wrap-jwt-to-ctia-auth-test
  (let [handler (fn [r]
                  {:body r
                   :status 200})
        wrapped-handler (sut/wrap-jwt-to-ctia-auth handler)
        request-no-jwt {:body "foo"
                        :url "http://localhost:8080/foo"}
        request-jwt (assoc request-no-jwt
                           :jwt {:sub "subject name"
                                 :business_guid "organization-id"})
        response-no-jwt (wrapped-handler request-no-jwt)
        response-jwt (wrapped-handler request-jwt)]
    (is (= {:body {:body "foo"
                   :url "http://localhost:8080/foo"}
            :status 200}
           response-no-jwt))
    (is (= {:body {:body "foo"
                   :url "http://localhost:8080/foo"
                   :jwt {:sub "subject name"
                         :business_guid "organization-id"}
                   :identity #ctia.auth.jwt.Identity{:jwt {:sub "subject name"
                                                           :business_guid "organization-id"}}
                   :groups ["organization-id"]
                   :login  "subject name"}
            :status 200}
           response-jwt))))
