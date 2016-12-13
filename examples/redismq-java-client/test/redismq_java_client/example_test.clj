(ns redismq-java-client.example-test
  (:require [cheshire.core :as json]
            [clj-momo.properties :as mp]
            [clojure.test :refer [deftest is testing]]
            [redismq.core :as rmq]
            [schema.core :as s])
  (:import [client Example]))

(deftest test-example
  (let [{{:keys [host port queue-name timeout-ms]} :redis}
        (->> (mp/read-property-files ["default-example.properties"
                                      "example.properties"])
             mp/transform
             (mp/coerce-properties {:redis {:host s/Str
                                            :port s/Int
                                            :timeout-ms s/Int
                                            :queue-name s/Str}}))

        queue
        (rmq/make-queue queue-name
                        {:host host
                         :port port
                         :timeout-ms timeout-ms}
                        {:max-depth 0})]

    (rmq/enqueue queue {:foo "bar"})

    (testing "Can read from the message queue"
      (is (= {:foo "bar"}
             (some-> (Example/getAnEvent host port queue-name)
                     (json/parse-string keyword)
                     :data))))))
