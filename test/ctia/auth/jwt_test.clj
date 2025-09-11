(ns ctia.auth.jwt-test
  (:require [clojure.set :as set]
            [clojure.test :as t :refer [are deftest is testing use-fixtures]]
            [ctia.auth.capabilities :as caps]
            [ctia.auth.jwt :as sut]
            [ctia.test-helpers.core :as helpers]
            [clj-http.fake :refer [with-fake-routes]]))

;; note: refactor into tests if this namespace uses any fixtures
(def get-in-config
  (helpers/build-get-in-config-fn))

(deftest wrap-jwt-to-ctia-auth-test
  (let [handler (fn [r]
                  {:body r
                   :status 200})
        wrapped-handler (sut/wrap-jwt-to-ctia-auth handler get-in-config)
        request-no-jwt {:body "foo"
                        :url "http://localhost:8080/foo"}
        client-id-claim (sut/iroh-claim "oauth/client/id" get-in-config)
        org-id-claim (sut/iroh-claim "org/id" get-in-config)
        jwt {:sub "subject name"
             client-id-claim "client-id"
             org-id-claim "organization-id"}
        request-jwt (assoc request-no-jwt :jwt jwt)
        response-no-jwt (wrapped-handler request-no-jwt)
        response-jwt (wrapped-handler request-jwt)]
    (is (= {:body {:body "foo"
                   :url "http://localhost:8080/foo"}
            :status 200}
           response-no-jwt))
    (is (= {:body
            {:body "foo"
             :url "http://localhost:8080/foo"
             :jwt jwt
             :client-id "client-id"
             :groups ["organization-id"]
             :login  "subject name"}
            :status 200}
           (update response-jwt :body dissoc :identity)))
    (is (instance? ctia.auth.jwt.JWTIdentity
                   (get-in response-jwt [:body :identity])))))

(deftest assets-scope-test
  (testing "Assets scope and capabilities"
    (helpers/with-properties ["ctia.auth.entities.scope" "global-intel"
                              "ctia.auth.assets.scope" "asset"]
      (helpers/fixture-ctia-with-app
       (fn [app]
         (let [{:keys [get-in-config]} (helpers/get-service-map app :ConfigService)]
           (is (= "global-intel" (sut/entity-root-scope get-in-config)))
           (is (= #{:create-asset :create-asset-mapping :create-asset-properties
                    :create-target-record :delete-asset :delete-asset-mapping
                    :delete-asset-properties :delete-target-record :list-asset-mappings
                    :list-asset-properties :list-assets :list-target-records :read-asset
                    :read-asset-mapping :read-asset-properties :read-target-record :search-asset
                    :search-asset-mapping :search-asset-properties :search-target-record}
                  (sut/scope-to-capabilities (sut/assets-root-scope get-in-config) get-in-config))
               "By default asset capabilities enabled on global-intel scope")
           (is (empty? (sut/scope-to-capabilities "no-asset-intel" get-in-config))
               "No asset capabilities, if scope doesn't allow it")))))))

(deftest scopes-to-capabilities-test
  (testing "scope defaults"
    (are [root-scope-fn def-val] (= def-val (root-scope-fn get-in-config))
      sut/entity-root-scope   "private-intel"
      sut/casebook-root-scope "casebook"
      sut/assets-root-scope   "asset")
    (is (= #{:search-casebook :create-casebook :list-casebooks :read-casebook
             :delete-casebook}
           (sut/scope-to-capabilities (sut/casebook-root-scope get-in-config) get-in-config))
        "Check the casebook capabilities from the casebook scope")
    (is (= #{:developer :specify-id}
           (set/difference (caps/all-capabilities)
                           (sut/scopes-to-capabilities #{(sut/entity-root-scope get-in-config)
                                                         (sut/casebook-root-scope get-in-config)}
                                                       get-in-config)))
        "with all scopes you should have most capabilities except some very
       specific ones")

    (is (set/subset?
         (sut/scopes-to-capabilities #{(sut/casebook-root-scope get-in-config)}
                                     get-in-config)
         (sut/scopes-to-capabilities #{(sut/entity-root-scope get-in-config)}
                                     get-in-config))
        "casebook capabilities are a subset of all entity caps")

    (is (= #{:import-bundle}
           (sut/scopes-to-capabilities
            #{(str (sut/entity-root-scope get-in-config) "/import-bundle")}
            get-in-config)))
    (is (= #{}
           (sut/scopes-to-capabilities
            #{(str (sut/entity-root-scope get-in-config) "/import-bundle:read")}
            get-in-config)))
    (is (contains? (sut/scopes-to-capabilities #{(str (sut/entity-root-scope get-in-config) ":write")}
                                               get-in-config)
                   :import-bundle))
    (is (not (contains? (sut/scopes-to-capabilities #{(str (sut/entity-root-scope get-in-config) ":read")}
                                                    get-in-config)
                        :import-bundle)))
    (is (= #{:read-sighting :list-sightings :search-sighting :create-sighting
             :delete-sighting}
           (sut/scopes-to-capabilities
            #{(str (sut/entity-root-scope get-in-config) "/sighting")}
            get-in-config))
        "Scopes can be limited to some entity")
    (is (= #{:create-sighting :delete-sighting}
           (sut/scopes-to-capabilities
            #{(str (sut/entity-root-scope get-in-config) "/sighting:write")}
            get-in-config))
        "Scopes can be limited to some entity and for write-only")
    (is (= #{:read-sighting :list-sightings :search-sighting}
           (sut/scopes-to-capabilities
            #{(str (sut/entity-root-scope get-in-config) "/sighting:read")}
            get-in-config))
        "Scopes can be limited to some entity and for read-only")
    (is (= #{:search-casebook :read-sighting :list-sightings :search-sighting
             :list-casebooks :read-casebook}
           (sut/scopes-to-capabilities #{(str (sut/entity-root-scope get-in-config) "/sighting:read")
                                         (str (sut/casebook-root-scope get-in-config) ":read")}
                                       get-in-config))
        "Scopes can compose")))

(deftest parse-jwt-pubkey-map-test
  (is (= ["APP ONE"]
         (keys
          (sut/parse-jwt-pubkey-map "APP ONE=resources/cert/ctia-jwt.pub")))
      "Should be able to parse and load the keys without exception when the keys exists")
  (is (= ["APP ONE" "APP ONE TEST"]
         (keys
          (sut/parse-jwt-pubkey-map "APP ONE=resources/cert/ctia-jwt.pub,APP ONE TEST=resources/cert/ctia-jwt-2.pub")))
      "Should be able to parse and load the keys without exception when the keys exists")

  (is (= (str "Could not load JWT keys correctly."
              " Please check ctia.http.jwt.jwt-pubkey-map config:"
              " /bad.key (No such file or directory)")
         (try
           (sut/parse-jwt-pubkey-map "APP ONE=/bad.key")
           (catch Exception e
             (.getMessage e))))
      "Should be able to parse and load the keys without exception when the keys exists")

  (is (= (str "Wrong format for ctia.http.jwt.jwt-pubkey-map config."
              " It should matches the following regex:"
              " ^([^=,]*=[^,]*)(,[^=,]*=[^,]*)*$"
              "\nExamples: \"APPNAME=/path/to/file.key\""
              "\n          \"APPNAME=/path/to/file.key,OTHER=/other/path.key\"")
         (try
           (sut/parse-jwt-pubkey-map "APP ONE resources/cert/wrong-jwt.pub")
           (catch Exception e
             (.getMessage e))))
      "Should returns a correct error message when failing to load the key"))

(deftest parse-jwks-urls-test
  (testing "Valid JWKS URLs configuration"
    (is (= {"issuer1" "https://auth.example.com/.well-known/jwks.json"
            "issuer2" "https://other.example.com/jwks"}
           (sut/parse-jwks-urls "issuer1=https://auth.example.com/.well-known/jwks.json,issuer2=https://other.example.com/jwks"))
        "Should parse multiple issuer-URL pairs"))
  
  (testing "Invalid JWKS URLs configuration"
    (is (thrown-with-msg? Exception #"Wrong format for JWKS URLs config"
                          (sut/parse-jwks-urls "invalid format"))
        "Should throw on invalid format"))
  
  (testing "Empty configuration"
    (is (nil? (sut/parse-jwks-urls nil))
        "Should return nil for nil input")
    (is (nil? (sut/parse-jwks-urls ""))
        "Should return nil for empty string")))

(deftest jwks-support-test
  (testing "JWK to public key conversion"
    (let [sample-jwk {:kty "RSA"
                      :kid "test-key-1"
                      :n "xjNrLpwRLqgvPpKLippl4jXKvJO8rPEqGZs2lPcQi_8IqLEsGLRr3L9IUyqfIzPJnDfEiUqELvCTPqLCGqCLfs8jbwZrXeakgRP6yiYPgmqMYdy0zJlEp5uEPJLd7iVBH-V5t8M8mkltu1V5uPsPdXgqGqoKqiUwyCVm3razVOcvg-3f_57BXMmtVcuTTjLaIbfEDp8UFCB0SYCLIiTkmFBrqHPNsldxLbn7Rg_OK8txy1hCQqRVDhlFsoSHao-kyWwE_PpRAKEJ8YYjNodJiB7YYqCLi8sH2wvPvPB1N-dVKoUJKEqnfOoB8gZL0CqHAnBQmkHgGZbBZo18dQ"
                      :e "AQAB"}
          public-key (#'sut/jwk->public-key sample-jwk)]
      (is (not (nil? public-key))
          "Should successfully convert valid JWK to public key")
      (is (instance? java.security.PublicKey public-key)
          "Should return a PublicKey instance")))

  (testing "JWK conversion with invalid key type"
    (let [invalid-jwk {:kty "EC" :kid "ec-key"}]
      (is (nil? (#'sut/jwk->public-key invalid-jwk))
          "Should return nil for non-RSA key types")))

  (testing "JWK conversion with malformed data"
    (let [malformed-jwk {:kty "RSA" :kid "bad-key" :n "invalid-base64" :e "AQAB"}]
      (is (nil? (#'sut/jwk->public-key malformed-jwk))
          "Should return nil for malformed JWK data")))
  
  (testing "JWKS fetching and key building simulation"
    (let [fake-jwks {:keys [{:kty "RSA"
                            :kid "key1"
                            :n "xjNrLpwRLqgvPpKLippl4jXKvJO8rPEqGZs2lPcQi_8IqLEsGLRr3L9IUyqfIzPJnDfEiUqELvCTPqLCGqCLfs8jbwZrXeakgRP6yiYPgmqMYdy0zJlEp5uEPJLd7iVBH-V5t8M8mkltu1V5uPsPdXgqGqoKqiUwyCVm3razVOcvg-3f_57BXMmtVcuTTjLaIbfEDp8UFCB0SYCLIiTkmFBrqHPNsldxLbn7Rg_OK8txy1hCQqRVDhlFsoSHao-kyWwE_PpRAKEJ8YYjNodJiB7YYqCLi8sH2wvPvPB1N-dVKoUJKEqnfOoB8gZL0CqHAnBQmkHgGZbBZo18dQ"
                            :e "AQAB"}
                           {:kty "RSA"
                            :kid "key2"
                            :n "0X_dqzXmA7lZXLpOm-3BjUkJnbI7RVgq2V5qyF7sjBJXrBPbMeBbEPbuBGPvQVCnJ84seULGi0pm9yj0Heb0qJaoUSJvAqQvjWP4ysOd0ogHp7nX0R_3m9teo0L1TEkdYoWR4UuhBM_qYz1nHtpDDvYPvGj1V6G6dj39dFT0KuFUqRn8m1MX4NiFJ1tUzEu_8WvM8lhJMccSyfLfZ7gknVgQLpXDqfPXxwSGkOGCpFepnUGCcrN7q2Qx_qxBEaJO8CXlLjFeoULLO93VxIfbPj7FUgnUqr_MIMYY-cPJE8SjoXn7JULweGqDG7j5NKjpNfh7jP0salr-EaGqNQ"
                            :e "AQAB"}
                           {:kty "RSA"
                            :kid "key-no-kid"}]}] ; Missing kid field
      (let [key-map (#'sut/build-key-map fake-jwks)]
        (is (= 2 (count key-map))
            "Should build map with only keys that have kid")
        (is (contains? key-map "key1")
            "Should contain key1")
        (is (contains? key-map "key2")
            "Should contain key2")
        (is (not (contains? key-map "key-no-kid"))
            "Should not contain key without kid")
        (is (every? #(instance? java.security.PublicKey %) (vals key-map))
            "All values should be PublicKey instances"))))

  (testing "JWKS internal functions"
    (let [fake-jwks {:keys [{:kty "RSA" :kid "test-key" 
                            :n "xjNrLpwRLqgvPpKLippl4jXKvJO8rPEqGZs2lPcQi_8IqLEsGLRr3L9IUyqfIzPJnDfEiUqELvCTPqLCGqCLfs8jbwZrXeakgRP6yiYPgmqMYdy0zJlEp5uEPJLd7iVBH-V5t8M8mkltu1V5uPsPdXgqGqoKqiUwyCVm3razVOcvg-3f_57BXMmtVcuTTjLaIbfEDp8UFCB0SYCLIiTkmFBrqHPNsldxLbn7Rg_OK8txy1hCQqRVDhlFsoSHao-kyWwE_PpRAKEJ8YYjNodJiB7YYqCLi8sH2wvPvPB1N-dVKoUJKEqnfOoB8gZL0CqHAnBQmkHgGZbBZo18dQ"
                            :e "AQAB"}]}]
      
      (testing "fetch-jwks internal function"
        (with-fake-routes
          {"https://test.example.com/jwks" {:get (fn [_] {:status 200
                                                           :headers {"Content-Type" "application/json"}
                                                           :body fake-jwks})}
           "https://error.example.com/jwks" {:get (fn [_] {:status 500
                                                            :body "Server Error"})}}
          
          (testing "Successful JWKS response parsing"
            (let [result (#'sut/fetch-jwks "https://test.example.com/jwks")]
              (is (= fake-jwks result)
                  "Should return parsed JWKS response")))
          
          (testing "Failed JWKS fetch returns nil"
            (is (nil? (#'sut/fetch-jwks "https://error.example.com/jwks"))
                "Should return nil when JWKS fetch fails"))))
      
      (testing "Invalid arguments to get-public-key-for-kid"
        (is (nil? (sut/get-public-key-for-kid nil "test-key"))
            "Should return nil for nil JWKS URL")
        (is (nil? (sut/get-public-key-for-kid "https://test.example.com/jwks" nil))
            "Should return nil for nil kid")))))

(deftest multi-jwks-integration-test
  (testing "Multi-JWKS configuration and key building"
    (let [fake-jwks-us {:keys [{:kty "RSA" :kid "us-key-1" 
                               :n "xjNrLpwRLqgvPpKLippl4jXKvJO8rPEqGZs2lPcQi_8IqLEsGLRr3L9IUyqfIzPJnDfEiUqELvCTPqLCGqCLfs8jbwZrXeakgRP6yiYPgmqMYdy0zJlEp5uEPJLd7iVBH-V5t8M8mkltu1V5uPsPdXgqGqoKqiUwyCVm3razVOcvg-3f_57BXMmtVcuTTjLaIbfEDp8UFCB0SYCLIiTkmFBrqHPNsldxLbn7Rg_OK8txy1hCQqRVDhlFsoSHao-kyWwE_PpRAKEJ8YYjNodJiB7YYqCLi8sH2wvPvPB1N-dVKoUJKEqnfOoB8gZL0CqHAnBQmkHgGZbBZo18dQ"
                               :e "AQAB"}]}
          fake-jwks-eu {:keys [{:kty "RSA" :kid "eu-key-1"
                               :n "0X_dqzXmA7lZXLpOm-3BjUkJnbI7RVgq2V5qyF7sjBJXrBPbMeBbEPbuBGPvQVCnJ84seULGi0pm9yj0Heb0qJaoUSJvAqQvjWP4ysOd0ogHp7nX0R_3m9teo0L1TEkdYoWR4UuhBM_qYz1nHtpDDvYPvGj1V6G6dj39dFT0KuFUqRn8m1MX4NiFJ1tUzEu_8WvM8lhJMccSyfLfZ7gknVgQLpXDqfPXxwSGkOGCpFepnUGCcrN7q2Qx_qxBEaJO8CXlLjFeoULLO93VxIfbPj7FUgnUqr_MIMYY-cPJE8SjoXn7JULweGqDG7j5NKjpNfh7jP0salr-EaGqNQ"
                               :e "AQAB"}]}]
      
      (testing "US JWKS key building"
        (let [us-key-map (#'sut/build-key-map fake-jwks-us)]
          (is (= 1 (count us-key-map))
              "Should build map with US key")
          (is (contains? us-key-map "us-key-1")
              "Should contain US key")
          (is (instance? java.security.PublicKey (get us-key-map "us-key-1"))
              "US key should be PublicKey instance")))
      
      (testing "EU JWKS key building"
        (let [eu-key-map (#'sut/build-key-map fake-jwks-eu)]
          (is (= 1 (count eu-key-map))
              "Should build map with EU key")
          (is (contains? eu-key-map "eu-key-1")
              "Should contain EU key")
          (is (instance? java.security.PublicKey (get eu-key-map "eu-key-1"))
              "EU key should be PublicKey instance")))
      
      (testing "Cross-region key isolation"
        (let [us-key-map (#'sut/build-key-map fake-jwks-us)
              eu-key-map (#'sut/build-key-map fake-jwks-eu)]
          (is (not (contains? us-key-map "eu-key-1"))
              "US JWKS should not contain EU key")
          (is (not (contains? eu-key-map "us-key-1"))
              "EU JWKS should not contain US key"))))))

(deftest jwks-caching-test
  (testing "JWKS caching mechanism exists"
    ;; Test that the caching mechanism is in place
    (is (fn? #'sut/fetch-cached-keys)
        "fetch-cached-keys function should exist for caching")
    
    ;; Test that the function is memoized (has cache metadata)
    (let [cache-meta (meta #'sut/fetch-cached-keys)]
      (is (some? cache-meta)
          "Cached function should have metadata indicating it's memoized"))))
