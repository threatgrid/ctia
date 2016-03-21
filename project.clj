(defproject ctia "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [clj-time "0.9.0"] ; required due to bug in lein-ring
                 [metosin/schema-tools "0.7.0"]
                 [com.rpl/specter "0.9.2"]
                 [org.clojure/core.async "0.2.374"]
                 [clj-http "2.0.1"]
                 [org.clojure/core.memoize "0.5.8"]

                 ;; Web server
                 [metosin/compojure-api "1.0.0"]
                 [ring-middleware-format "0.7.0"]

                 ;; Database
                 [clojurewerkz/elastisch "2.2.1"]
                 [korma "0.4.2"]
                 [org.clojure/java.jdbc "0.3.7"] ; specified by korma

                 ;; Docs
                 [markdown-clj "0.9.86"]
                 [hiccup "1.0.5"]

                 ;; Config
                 [cprop "0.1.6"]]

  :resource-paths ["resources" "doc"]
  :ring {:handler ctia.handler/app
         :init ctia.init/init!
         :nrepl {:start? true}}
  :uberjar-name "server.jar"
  :min-lein-version "2.4.0"
  :test-selectors {:es-store #(.contains (name (:name %)) "-es-store")
                   :default #(not (or (.contains (name (:name %)) "-es-store")
                                      (:integration %)
                                      (:regression %)))
                   :integration #(or (.contains (name (:name %)) "-es-store")
                                     (:integation %))}

  :profiles {:dev {:jvm-opts ["-Dconf=resources/config.edn"]
                   :dependencies [[cheshire "5.5.0"]
                                  [javax.servlet/servlet-api "2.5"]
                                  [ring/ring-jetty-adapter "1.4.0"]
                                  [com.h2database/h2 "1.4.191"]
                                  [org.clojure/test.check "0.9.0"]]
                   :plugins [[lein-ring "0.9.6"]]
                   :resource-paths ["model"
                                    "test/resources"]}})
