(def cheshire-version "5.6.3")
(def compojure-api-version "1.1.8")
(def schema-tools-version "0.9.0")
(def schema-version "1.1.3")

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

(defproject ctia "0.1.0-SNAPSHOT"
  :description "Cisco Threat Intelligence API"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}

  :jvm-opts ["-Xmx4g" ;; On some OSX VMs, this is needed to increase available memory
             "-Djava.awt.headless=true"
             "-XX:MaxPermSize=256m"
             "-Dlog.console.threshold=INFO"
             "-server"]
  :pedantic? :warn
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clj-time "0.12.0"]
                 [org.clojure/core.async "0.2.374"]
                 [org.slf4j/slf4j-log4j12 "1.7.21"]
                 [org.clojure/core.memoize "0.5.8"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.clojure/tools.cli "0.3.5"]
                 [pandect "0.6.0"]
                 [threatgrid/clj-momo "0.2.1"]

                 ;; Schemas
                 [prismatic/schema ~schema-version]
                 [metosin/schema-tools ~schema-tools-version]
                 [threatgrid/ctim "0.3.1"
                  ;; Exclusions:
                  ;; - ring-swagger as 0.22.9 lacks s/Any support
                  :exclusions [metosin/ring-swagger]]

                 ;; Web server
                 ;; ring-swagger 0.22.10 provided by compojure-api
                 ;; 1.1.8 fails to generate JSON schema for s/Any
                 ;; overriding here
                 [metosin/ring-swagger "0.22.11"]
                 [metosin/compojure-api ~compojure-api-version
                  ;; Exclusions:
                  ;; - ring-swagger as CTIM provides 0.22.9
                  ;; - compojure-api 1.1.7 is not using the latest snakeyaml
                  ;;  - As of 2016-08-25, the latest version is 1.17, elastich
                  ;;    3.0.0-beta1 wants 1.15, while compojure-api wants 1.13
                  :exclusions [org.yaml/snakeyaml metosin/ring-swagger]]
                 [ring/ring-jetty-adapter "1.5.0"]
                 [javax.servlet/servlet-api "2.5"]
                 [ring/ring-devel "1.4.0"]
                 [ring/ring-codec "1.0.1"
                  ;; Exclusions:
                  ;; - ring-codec 1.0.1 is not using the latest commons-codec
                  ;;   - As of 2016-08-25, the latest version is 1.10 (using 1.6)
                  :exclusions [commons-codec]]

                 ;; nREPL server
                 [org.clojure/tools.nrepl "0.2.12"]
                 [cider/cider-nrepl "0.14.0"]

                 ;; Database
                 [clojurewerkz/elastisch "3.0.0-beta1"]
                 [durable-atom "0.0.3"]

                 ;; clients
                 [clj-http "2.2.0"
                  ;; Exclusions:
                  ;; - clj-http "2.2.0" (and even the 3.x series) is not using
                  ;;   the latest commons-io
                  ;;   - As of 2016-08-25, the latest version is 0.5 (using 0.4)
                  :exclusions [commons-io]]
                 [com.taoensso/carmine "2.12.2"]

                 ;; Metrics
                 [metrics-clojure "2.7.0"]
                 [metrics-clojure-jvm "2.7.0"]
                 [metrics-clojure-ring "2.7.0"]
                 [metrics-clojure-riemann "2.7.0"]
                 [clout "2.1.2"]
                 [slugger "1.0.1"]
                 [riemann-clojure-client "0.4.2"]
                 [com.google.protobuf/protobuf-java "2.6.1"]

                 ;; Docs
                 [markdown-clj "0.9.86"]
                 [hiccup "1.0.5"]


                 ;; Hooksx
                 [redismq "0.1.0-SNAPSHOT"]]
  :exclusions [;; We don't need CLJS, but it comes in via cljs-time (CTIM)
               com.andrewmcveigh/cljs-time]

  :resource-paths ["resources" "doc"]
  :aot [ctia.main]
  :main ctia.main
  :classpath ".:resources"
  :uberjar-name "ctia.jar"
  :uberjar-exclusions [#"ctia\.properties"]
  :min-lein-version "2.4.0"
  :test-selectors {:atom-store :atom-store
                   :es-store :es-store
                   :es-store-native :es-store-native
                   :multi-store :multi-store
                   :disabled :disabled
                   :default #(not= :disabled %)
                   :integration #(or (:es-store %)
                                     (:es-store-native %)
                                     (:multi-store %)
                                     (:integration %)
                                     (:es-filtered-alias %)
                                     (:es-aliased-index %))
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
                                  [com.gfredericks/test.chuck "0.2.6"]
                                  ;;[perforate "0.3.4"]
                                  [prismatic/schema-generators "0.1.0"
                                   :exclusions [prismatic/schema]]]
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
                                    [com.gfredericks/test.chuck "0.2.6"]
                                    [prismatic/schema-generators "0.1.0"]]
                     :source-paths ["src","test","benchmarks"]}
             :test {:jvm-opts ["-Dlog.console.threshold=WARN"]
                    :dependencies [[cheshire ~cheshire-version]
                                   [org.clojure/test.check "0.9.0"]
                                   [com.gfredericks/test.chuck "0.2.6"]
                                   [prismatic/schema-generators "0.1.0"]]
                    :pedantic? :abort
                    :java-source-paths ["hooks/ctia"
                                        "test/java"]
                    :resource-paths ["test/resources"
                                     "test/resources/hooks/JarHook.jar"
                                     "test/resources/hooks/AutoloadHook.jar"
                                     "test/resources/hooks/hook-example-0.1.0-SNAPSHOT.jar"]}
             :prepush {:plugins [[yogsototh/lein-kibit "0.1.6-SNAPSHOT"]
                                 [lein-bikeshed "0.3.0"]]}}
  :perforate {:environments [{:name :actor
                              :namespaces [ctia.http.routes.actor-bench]}
                             {:name :campaign
                              :namespaces [ctia.http.routes.campaign-bench]}
                             {:name :bulk
                              :namespaces [ctia.http.routes.bulk-bench]}]}
  :plugins [[lein-shell "0.5.0"]
            [perforate "0.3.4"]]
  :aliases {"kibit" ["with-profile" "prepush" "kibit"]
            "bikeshed" ["with-profile" "prepush" "bikeshed" "-m" "100"]

            "prepush" ^{:doc "Check code quality before pushing"}
            ["shell" "scripts/pre-push-check.sh"]

            "bench" ^{:doc (str "Launch benchmarks"
                                "; use `lein bench actor` to only launch"
                                " actor related benchmarks")}
            ["with-profile" "test" "perforate"]

            "init-properties" ^{:doc (str "create an initial `ctia.properties`"
                                          " using docker machine ip")}
            ["shell" "scripts/init-properties-for-docker.sh"]})
