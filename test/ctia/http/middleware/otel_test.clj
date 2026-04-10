(ns ctia.http.middleware.otel-test
  "Unit tests for the OTel http.route middleware."
  (:require
   [clojure.test :refer [deftest is testing]]
   [ctia.http.middleware.otel :refer [->path compile-route-table
                                         find-route-template wrap-otel-route]]
   [ctia.test-helpers.otel :refer [with-mock-span]]))

(def ^:private sample-route-table
  [["/ctia/indicator/search" :get]
   ["/ctia/indicator/:id" :get]
   ["/ctia/indicator/:id" :put]
   ["/ctia/indicator/:id" :delete]
   ["/ctia/sighting/:id" :get]
   ["/ctia/entity/:id/sub/:sub-id" :get]
   ["/ctia/version" :get]])

(defn ok-handler [_request] {:status 200})

(defn make-handler
  ([] (make-handler ok-handler))
  ([handler] (wrap-otel-route handler sample-route-table)))

(deftest wrap-otel-route-matches-parameterized-path
  (with-mock-span captured
    (let [handler (make-handler)]
      (handler {:request-method :get
                :uri "/ctia/indicator/indicator-123"})
      (is (= "/ctia/indicator/:id" (get @captured "http.route"))))))

(deftest wrap-otel-route-matches-exact-path
  (with-mock-span captured
    (let [handler (make-handler)]
      (handler {:request-method :get
                :uri "/ctia/indicator/search"
                :query-string "query=*"})
      (is (= "/ctia/indicator/search" (get @captured "http.route"))))))

(deftest wrap-otel-route-respects-http-method
  (with-mock-span captured
    (let [handler (make-handler)]
      (handler {:request-method :put
                :uri "/ctia/indicator/indicator-123"})
      (is (= "/ctia/indicator/:id" (get @captured "http.route"))))))

(deftest wrap-otel-route-no-match-for-wrong-method
  (with-mock-span captured
    (let [handler (make-handler)]
      (handler {:request-method :post
                :uri "/ctia/indicator/indicator-123"})
      (is (nil? (get @captured "http.route"))))))

(deftest wrap-otel-route-no-match-for-unknown-uri
  (with-mock-span captured
    (let [handler (make-handler)]
      (handler {:request-method :get
                :uri "/ctia/nonexistent/foo"})
      (is (nil? (get @captured "http.route"))))))

(deftest wrap-otel-route-delegates-to-handler
  (with-mock-span captured
    (let [handler (make-handler (fn [_] {:status 418}))
          response (handler {:request-method :get
                             :uri "/ctia/version"})]
      (testing "handler response is passed through"
        (is (= 418 (:status response)))))))

(deftest wrap-otel-route-matches-multi-param-path
  (with-mock-span captured
    (let [handler (make-handler)]
      (handler {:request-method :get
                :uri "/ctia/entity/ent-1/sub/sub-2"})
      (is (= "/ctia/entity/:id/sub/:sub-id" (get @captured "http.route"))))))

(deftest wrap-otel-route-head-falls-back-to-get
  (with-mock-span captured
    (let [handler (make-handler)]
      (handler {:request-method :head
                :uri "/ctia/indicator/indicator-123"})
      (is (= "/ctia/indicator/:id" (get @captured "http.route"))))))

(deftest wrap-otel-route-propagates-handler-exception
  (with-mock-span captured
    (let [handler (make-handler (fn [_] (throw (ex-info "boom" {}))))]
      (testing "http.route is set before handler throws"
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"boom"
              (handler {:request-method :get
                        :uri "/ctia/indicator/indicator-123"})))
        (is (= "/ctia/indicator/:id" (get @captured "http.route")))))))

;; --- ->path unit tests ---

(deftest ->path-strips-trailing-slash
  (is (= "/things" (->path "/things/"))))

(deftest ->path-preserves-root
  (is (= "/" (->path "/"))))

(deftest ->path-preserves-no-trailing-slash
  (is (= "/items/:id" (->path "/items/:id"))))

(deftest ->path-handles-nil
  (is (nil? (->path nil))))

;; --- find-route-template trailing-slash tests ---

(deftest find-route-template-trailing-slash-exact
  (let [compiled (compile-route-table sample-route-table)]
    (is (= "/ctia/indicator/search"
           (find-route-template compiled
                                {:request-method :get
                                 :uri "/ctia/indicator/search/"})))))

(deftest find-route-template-trailing-slash-parameterized
  (let [compiled (compile-route-table sample-route-table)]
    (is (= "/ctia/indicator/:id"
           (find-route-template compiled
                                {:request-method :get
                                 :uri "/ctia/indicator/indicator-123/"})))))

(deftest find-route-template-trailing-slash-multi-param
  (let [compiled (compile-route-table sample-route-table)]
    (is (= "/ctia/entity/:id/sub/:sub-id"
           (find-route-template compiled
                                {:request-method :get
                                 :uri "/ctia/entity/ent-1/sub/sub-2/"})))))
