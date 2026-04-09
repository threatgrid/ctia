(ns ctia.http.middleware.otel-route-test
  "Integration tests verifying that the http.route OTel span attribute
   is correctly set by CTIA's api-handler for both authenticated and
   unauthenticated requests."
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [compojure.api.routes :as api-routes]
   [ctia.http.handler :as handler]
   [ctia.http.middleware.otel :as otel]
   [ctia.test-helpers
    [core :as helpers]
    [es :as es-helpers]
    [otel :refer [with-mock-span]]]
   [ctia.auth.allow-all :as allow-all]
   [puppetlabs.trapperkeeper.app :as app]
   [schema.test :refer [validate-schemas]]))

(use-fixtures :each
  validate-schemas
  es-helpers/fixture-properties:es-store
  helpers/fixture-allow-all-auth
  helpers/fixture-ctia-fast)

(defn ctia-ring-handler
  "Build CTIA's Ring handler with OTel wrapping, mirroring server.clj."
  []
  (let [app (helpers/get-current-app)
        api-handler (handler/api-handler (app/service-graph app))]
    (otel/wrap-otel-route api-handler (api-routes/get-routes api-handler))))

(deftest http-route-set-on-authenticated-request-test
  (with-mock-span captured
    (let [handler (ctia-ring-handler)
          response (handler (-> {:request-method :get
                                 :uri "/ctia/indicator/indicator-123"
                                 :headers {"authorization" "allow-all"}}
                                (assoc :identity allow-all/identity-singleton)))]
      (testing "request completes"
        (is (some? (:status response))))
      (testing "http.route is set to the route template"
        (is (= "/ctia/indicator/:id" (get @captured "http.route")))))))

(deftest http-route-set-on-unauthenticated-request-test
  (with-mock-span captured
    (let [handler (ctia-ring-handler)
          response (handler {:request-method :get
                             :uri "/ctia/indicator/indicator-123"
                             :headers {}})]
      (testing "unauthenticated request returns 401"
        (is (= 401 (:status response))))
      (testing "http.route is still set even for 401 responses"
        (is (= "/ctia/indicator/:id" (get @captured "http.route")))))))
