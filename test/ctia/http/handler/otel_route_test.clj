(ns ctia.http.handler.otel-route-test
  "Integration tests for http.route OTel span attribution via
   clj-otel wrap-compojure-route in api-handler."
  (:require
   [clojure.test :refer [deftest is testing]]
   [compojure.api.api :refer [api]]
   [compojure.core :refer [wrap-routes]]
   [ctia.lib.compojure.api.core :refer [context routes GET]]
   [ring.util.http-response :refer [ok]]
   [steffan-westcott.clj-otel.api.trace.http :as trace-http])
  (:import
   [io.opentelemetry.api.common AttributeKey Attributes]
   [io.opentelemetry.api.trace Span SpanContext StatusCode]
   [io.opentelemetry.context Scope]))

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

(defn- build-test-handler
  "Build a minimal Ring handler with wrap-routes + wrap-compojure-route."
  [test-routes]
  (wrap-routes
   (api {}
        test-routes)
   trace-http/wrap-compojure-route))

(deftest http-route-set-on-matched-request-test
  (let [captured (atom {})
        span (mock-span captured)
        ^Scope scope (.makeCurrent span)
        handler (build-test-handler
                 (context "/ctia" []
                   (context "/indicator" []
                     (GET "/:id" []
                       (ok "found")))))]
    (try
      (let [response (handler {:request-method :get
                               :uri "/ctia/indicator/abc-123"
                               :headers {}})]
        (is (= 200 (:status response)))
        (is (= "/ctia/indicator/:id" (get @captured "http.route"))))
      (finally
        (.close scope)))))

(deftest http-route-set-on-error-test
  (let [captured (atom {})
        span (mock-span captured)
        ^Scope scope (.makeCurrent span)
        handler (build-test-handler
                 (context "/ctia" []
                   (context "/indicator" []
                     (GET "/:id" []
                       (throw (ex-info "handler error" {}))))))]
    (try
      (testing "handler throws but http.route is still set"
        (let [response (handler {:request-method :get
                                 :uri "/ctia/indicator/abc-123"
                                 :headers {}})]
          (is (= 500 (:status response)))
          (is (= "/ctia/indicator/:id" (get @captured "http.route")))))
      (finally
        (.close scope)))))

(deftest http-route-not-set-on-unmatched-request-test
  (let [captured (atom {})
        span (mock-span captured)
        ^Scope scope (.makeCurrent span)
        handler (build-test-handler
                 (context "/ctia" []
                   (context "/indicator" []
                     (GET "/:id" []
                       (ok "found")))))]
    (try
      (handler {:request-method :get
                :uri "/ctia/nonexistent"
                :headers {}})
      (is (nil? (get @captured "http.route")))
      (finally
        (.close scope)))))

(deftest http-route-includes-context-prefix-test
  (let [captured (atom {})
        span (mock-span captured)
        ^Scope scope (.makeCurrent span)
        handler (build-test-handler
                 (context "/ctia" []
                   (context "/sighting" []
                     (GET "/" []
                       (ok "list")))))]
    (try
      (let [response (handler {:request-method :get
                               :uri "/ctia/sighting/"
                               :headers {}})]
        (is (= 200 (:status response)))
        (is (= "/ctia/sighting/" (get @captured "http.route"))))
      (finally
        (.close scope)))))
