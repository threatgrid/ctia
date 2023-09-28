(ns ctia.schemas.core-test
  (:require [clojure.test :refer [deftest is]]
            [ctia.schemas.core :as sut]))

(deftest transient-id?-test
  (is (not (sut/transient-id? nil)))
  (is (not (sut/transient-id? "")))
  (is (not (sut/transient-id? "incident-1234")))
  (is (not (sut/transient-id? "incident-0e14cef7-fbd9-4c06-a6d6-332ad82d5b32")))
  (is (not (sut/transient-id? "http://localhost:3000/ctia/incident/incident-0e14cef7-fbd9-4c06-a6d6-332ad82d5b32")))
  (is (sut/transient-id? "transient:incident-1234")))

(deftest non-transient-id?-test
  (is (not (sut/non-transient-id? nil)))
  (is (not (sut/non-transient-id? "")))
  (is (not (sut/non-transient-id? "incident-1234")))
  (is (sut/non-transient-id? "incident-0e14cef7-fbd9-4c06-a6d6-332ad82d5b32"))
  (is (sut/non-transient-id? "http://localhost:3000/ctia/incident/incident-0e14cef7-fbd9-4c06-a6d6-332ad82d5b32"))
  (is (not (sut/non-transient-id? "transient:incident-1234"))))
