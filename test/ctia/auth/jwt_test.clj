(ns ctia.auth.jwt-test
  (:require [ctia.auth.jwt :as sut]
            [ctia.auth.capabilities :as caps]
            [clojure.test :as t :refer [deftest is]]
            [clojure.set :as set]))

(deftest wrap-jwt-to-ctia-auth-test
  (let [handler (fn [r]
                  {:body r
                   :status 200})
        wrapped-handler (sut/wrap-jwt-to-ctia-auth handler)
        request-no-jwt {:body "foo"
                        :url "http://localhost:8080/foo"}
        request-jwt (assoc request-no-jwt
                           :jwt {:sub "subject name"
                                 (sut/iroh-claim "org/id") "organization-id"})
        response-no-jwt (wrapped-handler request-no-jwt)
        response-jwt (wrapped-handler request-jwt)]
    (is (= {:body {:body "foo"
                   :url "http://localhost:8080/foo"}
            :status 200}
           response-no-jwt))
    (is (= {:body {:body "foo"
                   :url "http://localhost:8080/foo"
                   :jwt {:sub "subject name"
                         (sut/iroh-claim "org/id") "organization-id"}
                   :identity #ctia.auth.jwt.JWTIdentity {:jwt {:sub "subject name"
                                                            "https://schemas.cisco.com/iroh/identity/claims/org/id" "organization-id"}}
                   :groups ["organization-id"]
                   :login  "subject name"}
            :status 200}
           response-jwt))))


(deftest scopes-to-capapbilities-test
  (is (= "private-intel" sut/entity-root-scope)
      "entity root scope default value is private-intel")
  (is (= "casebook" sut/casebook-root-scope)
      "casebook root scope default value is casebook")
  (is (= #{:search-casebook :create-casebook :list-casebooks :read-casebook
           :delete-casebook}
         (sut/scope-to-capabilities sut/casebook-root-scope))
      "Check the casebook capabilities from the casebook scope")
  (is  (= #{:developer :specify-id :external-id}
          (set/difference caps/all-capabilities
                          (sut/scopes-to-capabilities #{sut/entity-root-scope
                                                        sut/casebook-root-scope})))
       "with all scopes you should have most capabilities except some very
       specific ones")
  (is (= #{:import-bundle}
         (sut/scopes-to-capabilities
          #{(str sut/entity-root-scope "/import-bundle")})))
  (is (= #{}
         (sut/scopes-to-capabilities
          #{(str sut/entity-root-scope "/import-bundle:read")})))
  (is (contains? (sut/scopes-to-capabilities #{(str sut/entity-root-scope ":write")})
                 :import-bundle))
  (is (not (contains? (sut/scopes-to-capabilities #{(str sut/entity-root-scope ":read")})
                      :import-bundle)))
  (is (= #{:read-sighting :list-sightings :search-sighting :create-sighting
           :delete-sighting}
         (sut/scopes-to-capabilities
          #{(str sut/entity-root-scope "/sighting")}))
      "Scopes can be limited to some entity")
  (is (= #{:create-sighting :delete-sighting}
         (sut/scopes-to-capabilities
          #{(str sut/entity-root-scope "/sighting:write")}))
      "Scopes can be limited to some entity and for write-only")
  (is (= #{:read-sighting :list-sightings :search-sighting}
         (sut/scopes-to-capabilities
          #{(str sut/entity-root-scope "/sighting:read")}))
      "Scopes can be limited to some entity and for read-only")
  (is (= #{:search-casebook :read-sighting :list-sightings :search-sighting
           :list-casebooks :read-casebook}
         (sut/scopes-to-capabilities #{(str sut/entity-root-scope "/sighting:read")
                                       (str sut/casebook-root-scope ":read")}))
      "Scopes can compose"))
