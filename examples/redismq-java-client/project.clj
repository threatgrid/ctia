(defproject redismq-java-client "0.1.0-SNAPSHOT"
  :description "Java example using redismq"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [threatgrid/redismq "0.1.0-SNAPSHOT"]
                 [biz.paluch.redis/lettuce "4.3.0.Final"]
                 [threatgrid/clj-momo "0.2.2"]]
  :source-paths []
  :java-source-paths ["src"]
  :profiles {:dev {:resource-paths ["test/resources"]}})
