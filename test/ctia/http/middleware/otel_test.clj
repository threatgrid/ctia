(ns ctia.http.middleware.otel-test
  "Unit tests for the OTel http.route middleware."
  (:require
   [clojure.test :refer [deftest is testing]]
   [ctia.http.middleware.otel :refer [wrap-otel-route]]
   [ctia.test-helpers.otel :refer [with-mock-span]]))

(def ^:private sample-route-table
  ;; Exact paths before parameterized ones, matching compojure-api ordering.
  [["/ctia/indicator/search" :get]
   ["/ctia/indicator/:id" :get]
   ["/ctia/indicator/:id" :put]
   ["/ctia/indicator/:id" :delete]
   ["/ctia/sighting/:id" :get]
   ["/ctia/entity/:id/sub/:sub-id" :get]
   ["/ctia/version" :get]])

(defn ok-handler [_request] {:status 200})

(deftest wrap-otel-route-matches-parameterized-path
  (with-mock-span captured
    (let [handler (wrap-otel-route ok-handler sample-route-table)]
      (handler {:request-method :get
                :uri "/ctia/indicator/indicator-123"})
      (is (= "/ctia/indicator/:id" (get @captured "http.route"))))))

(deftest wrap-otel-route-matches-exact-path
  (with-mock-span captured
    (let [handler (wrap-otel-route ok-handler sample-route-table)]
      (handler {:request-method :get
                :uri "/ctia/indicator/search"
                :query-string "query=*"})
      (is (= "/ctia/indicator/search" (get @captured "http.route"))))))

(deftest wrap-otel-route-respects-http-method
  (with-mock-span captured
    (let [handler (wrap-otel-route ok-handler sample-route-table)]
      (handler {:request-method :put
                :uri "/ctia/indicator/indicator-123"})
      (is (= "/ctia/indicator/:id" (get @captured "http.route"))))))

(deftest wrap-otel-route-no-match-for-wrong-method
  (with-mock-span captured
    (let [handler (wrap-otel-route ok-handler sample-route-table)]
      (handler {:request-method :post
                :uri "/ctia/indicator/indicator-123"})
      (is (nil? (get @captured "http.route"))))))

(deftest wrap-otel-route-no-match-for-unknown-uri
  (with-mock-span captured
    (let [handler (wrap-otel-route ok-handler sample-route-table)]
      (handler {:request-method :get
                :uri "/ctia/nonexistent/foo"})
      (is (nil? (get @captured "http.route"))))))

(deftest wrap-otel-route-delegates-to-handler
  (with-mock-span captured
    (let [handler (wrap-otel-route (fn [_] {:status 418}) sample-route-table)
          response (handler {:request-method :get
                             :uri "/ctia/version"})]
      (testing "handler response is passed through"
        (is (= 418 (:status response)))))))

(deftest wrap-otel-route-matches-multi-param-path
  (with-mock-span captured
    (let [handler (wrap-otel-route ok-handler sample-route-table)]
      (handler {:request-method :get
                :uri "/ctia/entity/ent-1/sub/sub-2"})
      (is (= "/ctia/entity/:id/sub/:sub-id" (get @captured "http.route"))))))

(deftest wrap-otel-route-propagates-handler-exception
  (with-mock-span captured
    (let [handler (wrap-otel-route (fn [_] (throw (ex-info "boom" {})))
                                   sample-route-table)]
      (testing "http.route is set before handler throws"
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"boom"
              (handler {:request-method :get
                        :uri "/ctia/indicator/indicator-123"})))
        (is (= "/ctia/indicator/:id" (get @captured "http.route")))))))
