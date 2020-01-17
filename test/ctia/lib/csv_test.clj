(ns ctia.lib.csv-test
  (:require [ctia.lib.csv :as sut]
            [clojure.string :as string]
            [clojure.test :as t]
            [ctim.examples.judgements :as ju]))

(t/deftest to-csv-test
  (t/is (= ["\"external_references url\""
            "\"confidence\""
            "\"tlp\""
            "\"priority\""
            "\"id\""
            "\"timestamp\""
            "\"revision\""
            "\"external_references description\""
            "\"disposition\"" "\"observable value\""
            "\"external_references external_id\""
            "\"external_references hashes\""
            "\"disposition_name\""
            "\"schema_version\""
            "\"source\""
            "\"type\""
            "\"reason\""
            "\"source_uri\""
            "\"language\""
            "\"severity\""
            "\"external_references source_name\""
            "\"valid_time start_time\""
            "\"valid_time end_time\""
            "\"reason_uri\""
            "\"external_ids\""
            "\"observable type\""]
           (-> (sut/to-csv [ju/judgement-maximal
                            ju/judgement-maximal] true)
               (string/split #"\n")
               first
               (string/split #",")))
        "The internal map should be flattened and listed in the first line of
         the CSV if include headers is true")

  (t/is (= ["\"192.168.42.42\"" "\"foo@bar.com\""]
           (-> (sut/to-csv [{:value "192.168.42.42"}
                            {:value "foo@bar.com"}] false)
               (string/split #"\n")))
        "The internal map should not be listed if include headers is false")

  (t/is (= '(["\"external_references url\""
              "\"confidence\""
              "\"tlp\""
              "\"priority\""
              "\"id\""
              "\"timestamp\""
              "\"revision\""
              "\"external_references description\""
              "\"disposition\""
              "\"observable value\""
              "\"external_references external_id\""
              "\"external_references hashes\""
              "\"disposition_name\""
              "\"schema_version\""
              "\"source\""
              "\"type\""
              "\"reason\""
              "\"source_uri\""
              "\"language\""
              "\"severity\""
              "\"external_references source_name\""
              "\"valid_time start_time\""
              "\"valid_time end_time\""
              "\"reason_uri\""
              "\"external_ids\""
              "\"observable type\""]
             ["\"https://ex.tld/wiki/T1067\""
              "\"High\""
              "\"green\""
              "\"99\""
              "\"http://ex.tld/ctia/judgement/judgement-494d13ae-e914-43f0-883b-085062a8d9a1\""
              "\"#inst '2016-02-11T00:40:48.212-00:00'\""
              "\"1\""
              "\"Description text\""
              "\"1\""
              "\"10.0.0.1\""
              "\"T1067\""
              "\"['#section1']\""
              "\"Clean\""
              "\"1.0.14\""
              "\"source\""
              "\"judgement\""
              "\"reason\""
              "\"http://example.com/somewhere-else\""
              "\"language\""
              "\"Medium\""
              "\"source\""
              "\"#inst '2016-02-11T00:40:48.212-00:00'\""
              "\"#inst '2525-01-01T00:00:00.000-00:00'\""
              "\"http://example.com/a-really-good-reason\""
              "\"['123' 'ABC']\""
              "\"ip\""])
           (-> (sut/to-csv [ju/judgement-maximal] true)
               (string/split #"\n")
               (->> (map #(string/split %  #",")))))))

