(ns ctia.http.middleware.ratelimit-test
  (:require [ctia.http.middleware.ratelimit :as sut]
            [clojure.test :as t
             :refer [deftest is are join-fixtures use-fixtures testing]]
            [schema.core :as s]
            [schema.test :refer [validate-schemas]]
            [ctia.auth :as auth :refer [IIdentity]]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.es :as es-helpers]
            [taoensso.carmine :as car]
            [clj-momo.lib.clj-time.core :as time]))

(use-fixtures :once validate-schemas)

(defrecord FakeIdentity [groups limited?]
  IIdentity
  (authenticated? [_] true)
  (login [_] "user")
  (groups [_] groups)
  (client-id [_] nil)
  (allowed-capabilities [_] #{})
  (capable? [_ _] true)
  (rate-limit-fn [_ limit-fn] (when limited? limit-fn)))

(deftest with-limit-test
  (is (= [["tg:3" 10]
          ["tg:2" 30]
          ["tg:1" 20]]
         (sut/with-limit
           ["tg:3" "tg:2" "tg:1"]
           10
           {"tg:1" 20 "tg:2" 30}))))

(deftest parse-group-limits
  (is (= {"tg:1" 20
          "tg:2" 30}
         (sut/parse-group-limits "tg:1|20,tg:2|30"))))

(deftest sort-group-limits
  (is (= [["g3" 20] ["g2" 20] ["g1" 10]]
         (sut/sort-group-limits
          [["g1" 10] ["g2" 20] ["g3" 20]]))))

(deftest group-limit-fn-test
  (let [limit-fn (sut/group-limit-fn
                  {:limits {:group {:default 10
                                    :customs "tg:1|20,tg:2|30"}}})]
    (is (nil? (limit-fn {}))
        "The request shouldn't be limited if there is no identity in the request")
    (is (= {:nb-request-per-hour 10
            :rate-limit-key "tg:3"
            :name-in-headers "GROUP"}
           (limit-fn {:identity (->FakeIdentity ["tg:3"] true)})))
    (is (= {:nb-request-per-hour 30
            :rate-limit-key "tg:2"
            :name-in-headers "GROUP"}
           (limit-fn {:identity
                      (->FakeIdentity ["tg:3" "tg:2" "tg:1"] true)})))))

(deftest with-identity-rate-limit-fn
  (let [limit-fn (sut/with-identity-rate-limit-fn
                   (sut/group-limit-fn
                    {:limits {:group {:default 10
                                      :customs "tg:1|20,tg:2|30"}}}))]
    (is (= {:nb-request-per-hour 30
            :rate-limit-key "tg:2"
            :name-in-headers "GROUP"}
           (limit-fn
            {:identity (->FakeIdentity ["tg:3" "tg:2" "tg:1"] true)})))
    (is (nil?  (limit-fn
                {:identity (->FakeIdentity ["tg:3" "tg:2" "tg:1"]
                                           false)})))))

(def jwt-token "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJodHRwczovL3NjaGVtYXMuY2lzY28uY29tL2lyb2gvaWRlbnRpdHkvY2xhaW1zL3VzZXIvZW1haWwiOiJnYnVpc3NvbitxYV9zZGNfaXJvaEBjaXNjby5jb20iLCJodHRwczovL3NjaGVtYXMuY2lzY28uY29tL2lyb2gvaWRlbnRpdHkvY2xhaW1zL3VzZXIvaWRwL2lkIjoiYW1wIiwiaHR0cHM6Ly9zY2hlbWFzLmNpc2NvLmNvbS9pcm9oL2lkZW50aXR5L2NsYWltcy91c2VyL25pY2siOiJnYnVpc3NvbitxYV9zZGNfaXJvaEBjaXNjby5jb20iLCJlbWFpbCI6ImdidWlzc29uK3FhX3NkY19pcm9oQGNpc2NvLmNvbSIsInN1YiI6IjU2YmI1ZjhjLWNjNGUtNGVkMy1hOTFhLWM2NjA0Mjg3ZmUzMiIsImlzcyI6IklST0ggQXV0aCIsImh0dHBzOi8vc2NoZW1hcy5jaXNjby5jb20vaXJvaC9pZGVudGl0eS9jbGFpbXMvc2NvcGVzIjpbImNhc2Vib29rIiwiZ2xvYmFsLWludGVsIiwicHJpdmF0ZS1pbnRlbCIsImNvbGxlY3QiLCJlbnJpY2giLCJpbnNwZWN0IiwiaW50ZWdyYXRpb24iLCJpcm9oLWF1dGgiLCJyZXNwb25zZSIsInVpLXNldHRpbmdzIl0sImV4cCI6MTc4Nzc3Mjg1MCwiaHR0cHM6Ly9zY2hlbWFzLmNpc2NvLmNvbS9pcm9oL2lkZW50aXR5L2NsYWltcy9vYXV0aC9jbGllbnQvbmFtZSI6Imlyb2gtdWkiLCJodHRwczovL3NjaGVtYXMuY2lzY28uY29tL2lyb2gvaWRlbnRpdHkvY2xhaW1zL29yZy9pZCI6IjYzNDg5Y2Y5LTU2MWMtNDk1OC1hMTNkLTZkODRiN2VmMDlkNCIsImh0dHBzOi8vc2NoZW1hcy5jaXNjby5jb20vaXJvaC9pZGVudGl0eS9jbGFpbXMvb3JnL25hbWUiOiJJUk9IIFRlc3RpbmciLCJqdGkiOiJhMjY4YWU3YTMtMDljOS00MTQ5LWI0OTUtYjk4YzhjNWRlNjY2IiwibmJmIjoxNDg3MTY3NzUwLCJodHRwczovL3NjaGVtYXMuY2lzY28uY29tL2lyb2gvaWRlbnRpdHkvY2xhaW1zL3VzZXIvaWQiOiI1NmJiNWY4Yy1jYzRlLTRlZDMtYTkxYS1jNjYwNDI4N2ZlMzIiLCJodHRwczovL3NjaGVtYXMuY2lzY28uY29tL2lyb2gvaWRlbnRpdHkvY2xhaW1zL29hdXRoL2NsaWVudC9pZCI6Imlyb2gtdWkiLCJodHRwczovL3NjaGVtYXMuY2lzY28uY29tL2lyb2gvaWRlbnRpdHkvY2xhaW1zL3ZlcnNpb24iOiIxIiwiaWF0IjoxNDg3MTY4MDUwLCJodHRwczovL3NjaGVtYXMuY2lzY28uY29tL2lyb2gvaWRlbnRpdHkvY2xhaW1zL29hdXRoL2tpbmQiOiJzZXNzaW9uLXRva2VuIn0.Y1XTBTOeBIeV0nCGMKG08-AfNzAAwWQPV4hxXQnZClnb5AVvExRME2gAWIoLzHr7MN67UD7LeAifSSBStdRhCcLOX15Fn8n1TEhr3al6UCXfw3Rbx_8L7zEa6yNpXNbrlR_L4qIy1VUSFMV-uFvjQjitKtafBG8nWcl3zjUBAX7PRmG9kw_Lsyih84BgvqRSwglNkLU9gLsy03ghb8H64pjgfWe1VLzfi3mx5uwDMyW9vwR83auizlADqlijUqh1jKPRCo9INkMEEiRGBntqH5j6NJg9pdh9qa7jywEG8Tge0cu74kpNCyU_6AY5456x8JEpAaNtS63n5kfYVxCRSg")

(def reset-redis
  (fn [t]
    (car/wcar {} {} (car/flushdb))
    (t)))

(s/defn apply-fixtures-with-app
  [properties
   f-with-app :- (s/=> s/Any
                       (s/=> s/Any
                             (s/named s/Any 'app)))]
  (let [fixture-fn
        (join-fixtures [reset-redis
                        (helpers/fixture-properties:static-auth "user" "pwd")
                        #(helpers/with-properties properties (%))
                        es-helpers/fixture-properties:es-store
                        helpers/fixture-ctia
                        es-helpers/fixture-delete-store-indexes])]
    (fixture-fn
      (fn []
        (f-with-app
          (helpers/get-current-app))))))

(deftest rate-limit-test
  (with-redefs [time/now (constantly (time/date-time 2017 02 16 0 0 0))]
    (let [call (fn [app]
                 (helpers/GET app
                              "ctia/status"
                              :headers {"Authorization" (str "Bearer " jwt-token)
                                        "Origin" "http://external.cisco.com"}))]
      (testing "Group limit"
        (apply-fixtures-with-app
         ["ctia.http.rate-limit.limits.group.default" 5
          "ctia.http.rate-limit.enabled" true]
         (fn [app]
           (let [response (call app)]
             (testing "Rate limit headers"
               (is (= 200 (:status response)))
               (is (= "5" (get-in response [:headers "X-RateLimit-GROUP-Limit"]))))
             (testing "Rate limit status and response"
               (dotimes [_ 4] (call app))
               (let [response (call app)]
                 (is (= 429 (:status response)))
                 (is (contains? (->> (range 3590 3601) ;; avoid flakyness on slow test server.
                                     (map str)
                                     set)
                                (get-in response [:headers "Retry-After"])))
                 (is (= "{\"error\": \"Too Many Requests\"}"
                        (:body response)))))))))
      (testing "Custom group limits"
        (apply-fixtures-with-app
         ["ctia.http.rate-limit.limits.group.default" 15
          "ctia.http.rate-limit.limits.group.customs" "63489cf9-561c-4958-a13d-6d84b7ef09d4|4,tg:1|1"
          "ctia.http.rate-limit.enabled" true]
         (fn [app]
           (let [response (call app)]
             (is (= "4" (get-in response [:headers "X-RateLimit-GROUP-Limit"])))))))
      (testing "Unlimited client"
        (apply-fixtures-with-app
         ["ctia.http.rate-limit.limits.group.default" 15
          "ctia.http.rate-limit.limits.group.customs" "63489cf9-561c-4958-a13d-6d84b7ef09d4|4,tg:1|1"
          "ctia.http.rate-limit.unlimited.client-ids" "iroh-ui"
          "ctia.http.rate-limit.enabled" true]
         (fn [app]
           (let [response (call app)]
             (is (= 200 (:status response)))
             (is (nil? (get-in response [:headers "X-RateLimit-GROUP-Limit"])))))))
      (testing "rate limit disabled"
        (apply-fixtures-with-app
         ["ctia.http.rate-limit.limits.group.default" 15
          "ctia.http.rate-limit.limits.group.customs" "63489cf9-561c-4958-a13d-6d84b7ef09d4|4,tg:1|1"
          "ctia.http.rate-limit.enabled" false]
         (fn [app]
           (let [response (call app)]
             (is (= 200 (:status response)))))))
      (testing "Error handling (wrong redis port)"
        (apply-fixtures-with-app
         ["ctia.http.rate-limit.limits.group.default" 15
          "ctia.http.rate-limit.enabled" true
          "ctia.http.rate-limit.redis.port" 7887]
         (fn [app]
           (let [response (call app)]
             (is (= 200 (:status response)))
             (is (nil? (get-in response [:headers "X-RateLimit-GROUP-Limit"]))))))))))

