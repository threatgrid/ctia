(ns ctia.http.handler.otel-route-test
  "Integration tests verifying that the http.route OTel span attribute
   is correctly set by CTIA's api-handler via route-table matching."
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [ctia.http.handler :as handler]
   [ctia.test-helpers
    [core :as helpers]
    [es :as es-helpers]]
   [ctia.auth.allow-all :as allow-all]
   [puppetlabs.trapperkeeper.app :as app]
   [schema.test :refer [validate-schemas]])
  (:import
   [io.opentelemetry.api.common AttributeKey Attributes]
   [io.opentelemetry.api.trace Span SpanContext StatusCode]
   [io.opentelemetry.context Scope]))

(use-fixtures :each
  validate-schemas
  es-helpers/fixture-properties:es-store
  helpers/fixture-allow-all-auth
  helpers/fixture-ctia-fast)

(defn- mock-span
  "Creates a Span that captures setAttribute calls into the given atom."
  [captured-atom]
  (reify Span
    (^Span setAttribute [this ^AttributeKey key value]
      (swap! captured-atom assoc (.getKey key) value)
      this)
    (^Span addEvent [this ^String _name] this)
    (^Span addEvent [this ^String _name ^Attributes _attrs] this)
    (^Span setStatus [this ^StatusCode _code] this)
    (^Span setStatus [this ^StatusCode _code ^String _desc] this)
    (^Span recordException [this ^Throwable _ex] this)
    (^Span recordException [this ^Throwable _ex ^Attributes _attrs] this)
    (^Span updateName [this ^String _name] this)
    (end [_this])
    (^SpanContext getSpanContext [_this] (SpanContext/getInvalid))
    (^boolean isRecording [_this] true)))

(defn- ctia-ring-handler
  "Build CTIA's Ring handler from the current test app services."
  []
  (let [app (helpers/get-current-app)]
    (handler/api-handler (app/service-graph app))))

(defn- authenticated-request
  "Add allow-all identity to a request map so it passes wrap-authenticated."
  [request]
  (assoc request :identity allow-all/identity-singleton))

(deftest http-route-set-on-entity-get-test
  (let [captured (atom {})
        span (mock-span captured)
        ^Scope scope (.makeCurrent span)
        handler (ctia-ring-handler)]
    (try
      (let [response (handler (authenticated-request
                               {:request-method :get
                                :uri "/ctia/indicator/indicator-123"
                                :headers {"authorization" "allow-all"}}))]
        (testing "request completes"
          (is (some? (:status response))))
        (testing "http.route is set to the route template"
          (is (= "/ctia/indicator/:id" (get @captured "http.route")))))
      (finally
        (.close scope)))))

(deftest http-route-set-on-entity-search-test
  (let [captured (atom {})
        span (mock-span captured)
        ^Scope scope (.makeCurrent span)
        handler (ctia-ring-handler)]
    (try
      (let [response (handler (authenticated-request
                               {:request-method :get
                                :uri "/ctia/indicator/search"
                                :query-string "query=*"
                                :headers {"authorization" "allow-all"}}))]
        (testing "request completes"
          (is (some? (:status response))))
        (testing "http.route is set to the search route"
          (is (= "/ctia/indicator/search" (get @captured "http.route")))))
      (finally
        (.close scope)))))

(deftest http-route-not-set-on-unmatched-request-test
  (let [captured (atom {})
        span (mock-span captured)
        ^Scope scope (.makeCurrent span)
        handler (ctia-ring-handler)]
    (try
      (handler {:request-method :get
                :uri "/ctia/nonexistent-entity/foo"
                :headers {"authorization" "allow-all"}})
      (testing "http.route is not set for unmatched routes"
        (is (nil? (get @captured "http.route"))))
      (finally
        (.close scope)))))

(deftest http-route-set-on-version-test
  (let [captured (atom {})
        span (mock-span captured)
        ^Scope scope (.makeCurrent span)
        handler (ctia-ring-handler)]
    (try
      (let [response (handler {:request-method :get
                               :uri "/ctia/version"
                               :headers {}})]
        (testing "version endpoint returns 200"
          (is (= 200 (:status response))))
        (testing "http.route is set for version endpoint"
          (is (= "/ctia/version" (get @captured "http.route")))))
      (finally
        (.close scope)))))

(deftest http-route-set-on-unauthenticated-request-test
  (let [captured (atom {})
        span (mock-span captured)
        ^Scope scope (.makeCurrent span)
        handler (ctia-ring-handler)]
    (try
      (let [response (handler {:request-method :get
                               :uri "/ctia/indicator/indicator-123"
                               :headers {}})]
        (testing "unauthenticated request returns 401"
          (is (= 401 (:status response))))
        (testing "http.route is still set even for 401 responses"
          (is (= "/ctia/indicator/:id" (get @captured "http.route")))))
      (finally
        (.close scope)))))
