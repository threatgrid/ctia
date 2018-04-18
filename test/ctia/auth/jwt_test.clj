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
  (is (= "private-intel" sut/entity-root-scope))
  (is (= #{:create-attack-pattern :create-judgement :list-relationship
           :list-incident :create-exploit-target :read-actor :search-feedback
           :read-malware :create-investigation :delete-judgement
           :list-judgement :list-investigation :read-exploit-target
           :search-indicator :create-malware :delete-sightings :list-data-table
           :search-coa :read-sightings :read-attack-pattern :create-coa
           :create-verdict :list-malware :create-campaign :read-judgement
           :search-judgement :search-relationship :search-verdict :list-tool
           :list-attack-pattern :search-actor :delete-campaign :search-malware
           :delete-malware :create-relationship :list-sightings
           :delete-indicator :list-actor :search-campaign :read-verdict
           :create-incident :read-coa :read-indicator :delete-coa :list-verdict
           :create-sightings :read-feedback :search-investigation :delete-actor
           :list-feedback :create-data-table :list-indicator
           :list-exploit-target :create-indicator :delete-incident
           :create-feedback :delete-relationship :delete-data-table
           :search-incident :search-attack-pattern :delete-feedback
           :search-exploit-target :delete-verdict :read-campaign :list-coa
           :delete-investigation :search-data-table :search-sightings
           :read-relationship :create-tool :read-investigation :read-incident
           :create-actor :search-tool :read-tool :list-campaign
           :delete-attack-pattern :delete-exploit-target :delete-tool
           :read-data-table}
         (sut/scope-to-capabilities sut/entity-root-scope)))
  (is (= #{:search-casebook :create-casebook :list-casebook :read-casebook
           :delete-casebook}
         (sut/scope-to-capabilities sut/casebook-root-scope)))
  (is (= #{:search-casebook :create-attack-pattern :create-judgement
           :list-relationship :list-incident :create-exploit-target :read-actor
           :search-feedback :read-malware :create-investigation
           :delete-judgement :list-judgement :list-investigation
           :read-exploit-target :search-indicator :create-malware
           :delete-sightings :list-data-table :search-coa :read-sightings
           :read-attack-pattern :create-coa :create-verdict :list-malware
           :create-campaign :read-judgement :search-judgement
           :search-relationship :search-verdict :list-tool :list-attack-pattern
           :search-actor :delete-campaign :search-malware :delete-malware
           :create-relationship :list-sightings :delete-indicator :list-actor
           :search-campaign :read-verdict :create-incident :read-coa
           :read-indicator :delete-coa :list-verdict :create-sightings
           :read-feedback :search-investigation :delete-actor :list-feedback
           :create-data-table :list-indicator :list-exploit-target
           :create-indicator :delete-incident :create-feedback :create-casebook
           :delete-relationship :delete-data-table :search-incident
           :search-attack-pattern :delete-feedback :search-exploit-target
           :delete-verdict :read-campaign :list-coa :delete-investigation
           :search-data-table :search-sightings :read-relationship
           :list-casebook :create-tool :read-investigation :read-incident
           :create-actor :search-tool :read-tool :list-campaign
           :delete-attack-pattern :read-casebook :delete-casebook
           :delete-exploit-target :delete-tool :read-data-table}
         (sut/scopes-to-capabilities #{sut/entity-root-scope sut/casebook-root-scope})))
  (is (= #{:read-sightings :list-sightings :search-sightings}
         (sut/scopes-to-capabilities #{(str sut/entity-root-scope "/sightings:read")})))

  (is (= #{:search-casebook :read-sightings :list-sightings :search-sightings
           :list-casebook :read-casebook}
         (sut/scopes-to-capabilities #{(str sut/entity-root-scope "/sightings:read")
                                       (str sut/casebook-root-scope ":read")}))))
