(def cheshire-version "5.8.1")
(def compojure-api-version "1.1.11")
(def schema-tools-version "0.9.1")
(def schema-version "1.1.11")
(def jetty-server-version "9.4.15.v20190215")
;; On avoiding dependency overrides:
;; - :pedantic? should be set to :abort; Use "lein deps :tree" to resolve
;;   conflicts.  Do not change this in master.
;; - We have multiple deps that share child deps, particularly schema libs
;;   and we want to keep them in sync.
;; - If you can't update all deps to resolve conflicts, then use :exclusions,
;;   but try to minimize exclusions, as it may hide bugs
;; - If you use an exclusion, consider commenting where the conflict came from
;; - Open a github issue if you are stuck
;; - Common problem deps, as well as deps that are repeated in different
;;   profiles, should be def'ed above (in alphabetical order you barbarian!)
;; - If you update a dep that has :exclusions, check if each exclusions is still
;;   valid, and update the exclusions/comments accordingly
;; - Maybe you can just delete the dep! (doesn't hurt to check)

(defproject ctia "1.1.0"
  :description "Cisco Threat Intelligence API"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}

  :jvm-opts ["-Djava.awt.headless=true"
             "-Dlog.console.threshold=INFO"
             "-server"]
  :pedantic? :warn
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [clj-time "0.14.2"]
                 [org.clojure/core.async "0.3.465" :exclusions [org.clojure/tools.reader]]
                 [org.slf4j/slf4j-log4j12 "1.8.0-beta0"]
                 [org.clojure/core.memoize "0.5.9"]
                 [org.clojure/tools.logging "0.4.1"]
                 [org.clojure/tools.cli "0.4.1"]
                 [pandect "0.6.1"]

                 ;; Schemas
                 [prismatic/schema ~schema-version]
                 [metosin/schema-tools ~schema-tools-version]
                 [threatgrid/flanders "0.1.17"
                  :exclusions [prismatic/plumbing
                               potemkin
                               com.andrewmcveigh/cljs-time]]
                 [threatgrid/ctim "1.0.11"
                  :exclusions [threatgrid/flanders
                               metosin/ring-swagger
                               com.google.guava/guava
                               org.clojure/tools.reader]]
                 [threatgrid/clj-momo "0.2.33-SNAPSHOT"]

                 ;; Web server
                 [metosin/compojure-api ~compojure-api-version
                  :exclusions [com.google.code.findbugs/jsr305
                               com.andrewmcveigh/cljs-time
                               org.clojure/core.memoize
                               org.clojure/tools.reader]]
                 [org.eclipse.jetty/jetty-server ~jetty-server-version]
                 [ring/ring-jetty-adapter "1.7.1"
                  :exclusions [commons-io
                               clj-time
                               com.andrewmcveigh/cljs-time
                               org.eclipse.jetty/jetty-server]]
                 [ring/ring-devel "1.7.1"
                  :exclusions [joda-time
                               commons-io
                               clj-time
                               com.andrewmcveigh/cljs-time]]
                 [ring-cors "0.1.11"]
                 [ring/ring-codec "1.1.1"
                  ;; Exclusions:
                  ;; - ring-codec 1.0.1 is not using the latest commons-codec
                  ;;   - As of 2016-08-25, the latest version is 1.10 (using 1.6)
                  :exclusions [commons-codec]]
                 [yogsototh/clj-jwt "0.2.1"]
                 [threatgrid/ring-turnstile-middleware "0.1.0"
                  :exclusions [metosin/schema-tools]]
                 [threatgrid/ring-jwt-middleware "0.0.13"
                  :exclusions [metosin/compojure-api]]
                 [scopula "0.1.4"
                  :exclusions [org.clojure/spec.alpha
                               org.clojure/clojure
                               org.clojure/core.specs.alpha
                               com.andrewmcveigh/cljs-time]]

                 ;; clients
                 [clj-http "3.7.0" :exclusions [commons-codec]]
                 [com.taoensso/carmine "2.19.1"]

                 ;; Metrics
                 [metrics-clojure "2.10.0"]
                 [metrics-clojure-jvm "2.10.0"]
                 [metrics-clojure-ring "2.10.0"]
                 [metrics-clojure-riemann "2.10.0"]
                 [clout "2.1.2"]
                 [slugger "1.0.1"]
                 [riemann-clojure-client "0.4.5"]
                 [com.google.protobuf/protobuf-java "3.5.1"]

                 ;; Docs
                 [markdown-clj "1.0.1"]
                 [hiccup "2.0.0-alpha1"]

                 ;; Hooks
                 [threatgrid/redismq "0.1.1"]

                 [zookeeper-clj "0.9.3"]
                 [org.onyxplatform/onyx-kafka "0.14.5.0"
                  :exclusions [org.clojure/clojure
                               com.andrewmcveigh/cljs-time
                               com.stuartsierra/component
                               io.netty/netty
                               org.slf4j/slf4j-log4j12
                               org.apache.zookeeper/zookeeper
                               com.google.guava/guava
                               org.clojure/core.async]]

                 ;; GraphQL
                 [base64-clj "0.1.1"]
                 [threatgrid/ring-graphql-ui "0.1.1"
                  :exclusions [commons-fileupload
                               ring/ring-core
                               cheshire
                               metosin/ring-http-response]]
                 [com.graphql-java/graphql-java "9.4"
                  :exclusions [org.slf4j/slf4j-api]]]

  :exclusions [;; We don't need CLJS, but it comes in via cljs-time (CTIM)
               com.andrewmcveigh/cljs-time]

  :resource-paths ["resources" "doc"]
  :aot [ctia.main]
  :main ctia.main
  :classpath ".:resources"
  :uberjar-name "ctia.jar"
  :uberjar-exclusions [#"ctia\.properties"]
  :min-lein-version "2.4.0"
  :test-selectors {:es-store :es-store
                   :disabled :disabled
                   :default #(not (or (:disabled %)
                                      (:sleepy %)
                                      (:generative %)))
                   :integration #(or (:es-store %)
                                     (:integration %)
                                     (:es-aliased-index %))
                   :no-gen #(not (:generative %))
                   :all #(not (:disabled %))}

  :java-source-paths ["hooks/ctia"]
  :javac-options  ["-proc:none"] ;; remove a warning
  :filespecs [{:type :fn
               :fn (fn [p]
                     {:type :bytes :path "ctia-version.txt"
                      :bytes (str (:out (clojure.java.shell/sh
                                         "git" "log" "-n" "1" "--pretty=format:%H "))
                                  (:out (clojure.java.shell/sh
                                         "git" "symbolic-ref" "--short" "HEAD")))})}]

  :profiles {:dev {:dependencies [[cheshire ~cheshire-version]
                                  [org.clojure/test.check "0.9.0"]
                                  [com.gfredericks/test.chuck "0.2.8"]
                                  [prismatic/schema-generators "0.1.1"]]
                   :pedantic? :warn

                   :resource-paths ["test/resources"]}
             :jmx {:jvm-opts ["-Dcom.sun.management.jmxremote"
                              "-Dcom.sun.management.jmxremote.port=9010"
                              "-Dcom.sun.management.jmxremote.local.only=false"
                              "-Dcom.sun.management.jmxremote.authenticate=false"
                              "-Dcom.sun.management.jmxremote.ssl=false"]}
             :bench {:dependencies [[cheshire ~cheshire-version]
                                    [perforate "0.3.4"]
                                    [criterium "0.4.4"]
                                    [org.clojure/test.check "0.9.0"]
                                    [com.gfredericks/test.chuck "0.2.8"]
                                    [prismatic/schema-generators "0.1.1"]]
                     :source-paths ["src","test","benchmarks"]}
             :test {:jvm-opts ["-Dlog.console.threshold=WARN"]
                    :dependencies [[cheshire ~cheshire-version]
                                   [org.clojure/test.check "0.9.0"]
                                   [com.gfredericks/test.chuck "0.2.8"]
                                   [prismatic/schema-generators "0.1.1"]]
                    :pedantic? :abort
                    :java-source-paths ["hooks/ctia"
                                        "test/java"]
                    :resource-paths ["test/resources"
                                     "test/resources/hooks/JarHook.jar"
                                     "test/resources/hooks/AutoloadHook.jar"
                                     "test/resources/hooks/hook-example-0.1.0-SNAPSHOT.jar"]}

             :dev-test {:pedantic? :warn}
             :prepush {:plugins [[yogsototh/lein-kibit "0.1.6-SNAPSHOT"]
                                 [lein-bikeshed "0.3.0"]]}}
  :perforate {:environments [{:name :actor
                              :namespaces [ctia.entity.actor-bench]}
                             {:name :campaign
                              :namespaces [ctia.entity.campaign-bench]}
                             {:name :bulk
                              :namespaces [ctia.bulk.routes-bench]}
                             {:name :migration
                              :namespaces [ctia.tasks.migrate-es-stores-bench]}
                             ]}
  :plugins [[lein-shell "0.5.0"]
            [perforate "0.3.4"]]
  :aliases {"dev-test" ["with-profile" "test,dev-test" "test"]
            "kibit" ["with-profile" "prepush" "kibit"]
            "bikeshed" ["with-profile" "prepush" "bikeshed" "-m" "100"]

            "prepush" ^{:doc "Check code quality before pushing"}
            ["shell" "scripts/pre-push-check.sh"]

            "bench" ^{:doc (str "Launch benchmarks"
                                "; use `lein bench actor` to only launch"
                                " actor related benchmarks")}
            ["with-profile" "test,dev-test" "perforate"]

            "init-properties" ^{:doc (str "create an initial `ctia.properties`"
                                          " using docker machine ip")}
            ["shell" "scripts/init-properties-for-docker.sh"]})
