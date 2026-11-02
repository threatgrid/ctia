(ns ctia.http.exceptions-test
  (:require  [ctia.http.exceptions :as sut]
             [clojure.test :refer [deftest is testing]]))

(deftest es-ex-data-test
  (let [exdata {:es "don't like it"}
        data {:some :stuff}
        exception (ex-info "ES is not happy"
                           exdata)
        query-params {:query "http://very.bad"}
        request {:authorization "Dont log me"
                 :query-params query-params}]
    (is (= {:query-params query-params
            :data data
            :ex-data exdata}
           (sut/es-ex-data exception data request)))))

(deftest unsafe-url-scheme-error-handler-test
  ;; XFV-135: pin the 400 status and error-type the handler produces. The
  ;; registration test (crud_test) asserts the :unsafe-url-scheme-error type maps
  ;; to THIS handler; this asserts the handler returns a 400 (not a 500 or 200).
  ;; Together they close the chain from url-scheme-check to the HTTP response, so
  ;; mutating `bad-request` here breaks a test.
  (testing "returns HTTP 400 with the Invalid URL Scheme Error type"
    (let [e (ex-info "Disallowed URL scheme in field(s): source_uri (javascript:)"
                     {:type :unsafe-url-scheme-error
                      :entity {:source_uri "javascript:alert(1)"}})
          {:keys [status body]} (sut/unsafe-url-scheme-error-handler e nil nil)]
      (is (= 400 status))
      (is (= "Invalid URL Scheme Error" (:type body))))))
