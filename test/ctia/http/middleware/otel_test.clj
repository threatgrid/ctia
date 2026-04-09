(ns ctia.http.middleware.otel-test
  "Unit tests for the OTel http.route middleware."
  (:require
   [clojure.test :refer [deftest is testing]]
   [ctia.http.middleware.otel :refer [wrap-otel-route]]
   [ctia.test-helpers.otel :refer [mock-span]])
  (:import
   [io.opentelemetry.context Scope]))

(def ^:private sample-route-table
  ;; Exact paths before parameterized ones, matching compojure-api ordering.
  [["/ctia/indicator/search" :get]
   ["/ctia/indicator/:id" :get]
   ["/ctia/indicator/:id" :put]
   ["/ctia/indicator/:id" :delete]
   ["/ctia/sighting/:id" :get]
   ["/ctia/entity/:id/sub/:sub-id" :get]
   ["/ctia/version" :get]])

(defn- ok-handler [_request] {:status 200})

(deftest wrap-otel-route-matches-parameterized-path
  (let [captured (atom {})
        span (mock-span captured)
        ^Scope scope (.makeCurrent span)
        handler (wrap-otel-route ok-handler sample-route-table)]
    (try
      (handler {:request-method :get
                :uri "/ctia/indicator/indicator-123"})
      (is (= "/ctia/indicator/:id" (get @captured "http.route")))
      (finally
        (.close scope)))))

(deftest wrap-otel-route-matches-exact-path
  (let [captured (atom {})
        span (mock-span captured)
        ^Scope scope (.makeCurrent span)
        handler (wrap-otel-route ok-handler sample-route-table)]
    (try
      (handler {:request-method :get
                :uri "/ctia/indicator/search"
                :query-string "query=*"})
      (is (= "/ctia/indicator/search" (get @captured "http.route")))
      (finally
        (.close scope)))))

(deftest wrap-otel-route-respects-http-method
  (let [captured (atom {})
        span (mock-span captured)
        ^Scope scope (.makeCurrent span)
        handler (wrap-otel-route ok-handler sample-route-table)]
    (try
      (handler {:request-method :put
                :uri "/ctia/indicator/indicator-123"})
      (is (= "/ctia/indicator/:id" (get @captured "http.route")))
      (finally
        (.close scope)))))

(deftest wrap-otel-route-no-match-for-wrong-method
  (let [captured (atom {})
        span (mock-span captured)
        ^Scope scope (.makeCurrent span)
        handler (wrap-otel-route ok-handler sample-route-table)]
    (try
      (handler {:request-method :post
                :uri "/ctia/indicator/indicator-123"})
      (is (nil? (get @captured "http.route")))
      (finally
        (.close scope)))))

(deftest wrap-otel-route-no-match-for-unknown-uri
  (let [captured (atom {})
        span (mock-span captured)
        ^Scope scope (.makeCurrent span)
        handler (wrap-otel-route ok-handler sample-route-table)]
    (try
      (handler {:request-method :get
                :uri "/ctia/nonexistent/foo"})
      (is (nil? (get @captured "http.route")))
      (finally
        (.close scope)))))

(deftest wrap-otel-route-delegates-to-handler
  (let [captured (atom {})
        span (mock-span captured)
        ^Scope scope (.makeCurrent span)
        handler (wrap-otel-route (fn [_] {:status 418}) sample-route-table)]
    (try
      (let [response (handler {:request-method :get
                               :uri "/ctia/version"})]
        (testing "handler response is passed through"
          (is (= 418 (:status response)))))
      (finally
        (.close scope)))))

(deftest wrap-otel-route-matches-multi-param-path
  (let [captured (atom {})
        span (mock-span captured)
        ^Scope scope (.makeCurrent span)
        handler (wrap-otel-route ok-handler sample-route-table)]
    (try
      (handler {:request-method :get
                :uri "/ctia/entity/ent-1/sub/sub-2"})
      (is (= "/ctia/entity/:id/sub/:sub-id" (get @captured "http.route")))
      (finally
        (.close scope)))))

(deftest wrap-otel-route-propagates-handler-exception
  (let [captured (atom {})
        span (mock-span captured)
        ^Scope scope (.makeCurrent span)
        handler (wrap-otel-route (fn [_] (throw (ex-info "boom" {})))
                                 sample-route-table)]
    (try
      (testing "http.route is set before handler throws"
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"boom"
              (handler {:request-method :get
                        :uri "/ctia/indicator/indicator-123"})))
        (is (= "/ctia/indicator/:id" (get @captured "http.route"))))
      (finally
        (.close scope)))))
