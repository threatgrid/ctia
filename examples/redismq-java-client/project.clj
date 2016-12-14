(defproject redismq-java-client "0.1.0-SNAPSHOT"
  :description "Java example using redismq"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [biz.paluch.redis/lettuce "4.3.0.Final"]
                 [org.json/json "20160810"]]
  :source-paths []
  :java-source-paths ["src"]
  :profiles {:dev {:resource-paths ["test/resources"]
                   :dependencies [[threatgrid/redismq "0.1.0-SNAPSHOT"]
                                  [threatgrid/ctim "0.4.2-SNAPSHOT"]
                                  [threatgrid/clj-momo "0.2.2"]]}})
