(def cheshire-version "5.10.0")
(def clj-http-fake-version "1.0.3")
(def clj-version (or (System/getenv "CLOJURE_VERSION")
                     "1.10.1"))
(def metrics-clojure-version "2.10.0")
(def perforate-version "0.3.4")
(def ring-version "1.8.0")
(def schema-generators-version "0.1.3")
(def test-check-version "1.0.0")
(def test-chuck-version "0.2.10")
(def trapperkeeper-version "3.1.0")

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

(defproject ctia "1.1.1-SNAPSHOT"
  :description "Cisco Threat Intelligence API"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}

  :jvm-opts ["-Djava.awt.headless=true"
             "-Dlog.console.threshold=INFO"
             "-server"]
  :exclusions [org.slf4j/log4j-over-slf4j] ;; remove from trapperkeeper jars
  ;; use `lein pom; mvn dependency:tree -Dverbose -Dexcludes=org.clojure:clojure`
  ;; to inspect conflicts.

  :dependencies [[org.clojure/clojure ~clj-version]
                 [clj-time "0.15.2"]
                 [clojure.java-time "0.3.2"]
                 [org.clojure/core.async "1.0.567"]
                 [org.slf4j/slf4j-log4j12 "1.8.0-beta0"]
                 [org.clojure/core.memoize "1.0.236"]
                 [org.clojure/tools.logging "1.1.0"]
                 [org.clojure/tools.cli "1.0.194"]
                 [pandect "0.6.1"]

                 ;; Trapperkeeper
                 [puppetlabs/trapperkeeper ~trapperkeeper-version]
                 [puppetlabs/kitchensink ~trapperkeeper-version]
                 [prismatic/plumbing "0.5.5"] ;; upgrade puppetlabs/trapperkeeper

                 ;; Schemas
                 [prismatic/schema "1.1.12"]
                 [metosin/schema-tools "0.12.2"]
                 [threatgrid/flanders "0.1.23"]

                 [threatgrid/ctim "1.0.23"]
                 [threatgrid/clj-momo "0.3.5"]
                 [threatgrid/ductile "0.2.0"]

                 [com.arohner/uri "0.1.2"]

                 ;; Web server
                 [metosin/compojure-api "1.1.13" ]
                 ;; optional dep for compojure-api's dep ring-middleware-format
                 ;; see: https://github.com/ngrunwald/ring-middleware-format/issues/74
                 [com.ibm.icu/icu4j "65.1"]
                 [metosin/ring-swagger "0.26.2"]
                 [metosin/ring-swagger-ui "3.24.3"]
                 [ring/ring-core ~ring-version] ;ring/ring-jetty-adapter > metosin/ring-swagger
                 [ring/ring-jetty-adapter ~ring-version]
                 [ring/ring-devel ~ring-version]
                 [ring-cors "0.1.13"]
                 [commons-codec "1.12"] ;threatgrid/ctim, threatgrid/clj-momo, clj-http > ring/ring-codec
                 [ring/ring-codec "1.1.2"]
                 [threatgrid/clj-jwt "0.3.1"]
                 [threatgrid/ring-turnstile-middleware "0.1.1"]
                 [threatgrid/ring-jwt-middleware "1.0.1"]
                 [scopula "0.1.4"]

                 ;; clients
                 [clj-http "3.10.1"]
                 [com.taoensso/carmine "2.19.1" #_"2.20.0-RC1"]
                 [cheshire ~cheshire-version] ;; upgrade threatgrid/ring-jwt-middleware, puppetlabs/kitchensink (+ a dozen others)

                 ;; Metrics
                 [metrics-clojure ~metrics-clojure-version]
                 [metrics-clojure-jvm ~metrics-clojure-version]
                 [metrics-clojure-ring ~metrics-clojure-version]
                 [clout "2.2.1"]
                 [slugger "1.0.1"]
                 [com.google.guava/guava "20.0"];org.onyxplatform/onyx-kafka > threatgrid/ctim
                 [io.netty/netty "3.10.6.Final"];org.onyxplatform/onyx-kafka > metrics-clojure-riemann, zookeeper-clj
                 [io.netty/netty-codec "4.1.42.Final"] ;org.apache.zookeeper/zookeeper > riemann-clojure-client
                 [io.netty/netty-resolver "4.1.42.Final"] ;riemann-clojure-client > org.apache.zookeeper/zookeeper
                 [com.google.protobuf/protobuf-java "3.11.1"] ;riemann-clojure-client > threatgrid:ctim, metrics-clojure-riemann, org.onyxplatform/onyx-kafka
                 [riemann-clojure-client "0.5.1"]
                 ;; https://stackoverflow.com/a/43574427
                 [jakarta.xml.bind/jakarta.xml.bind-api "2.3.2"]

                 ;; Docs
                 [markdown-clj "1.10.1"]
                 [hiccup "2.0.0-alpha2"]

                 ;; Encryption
                 [lock-key "1.5.0"]

                 ;; Hooks
                 [threatgrid/redismq "0.1.1"]

                 [org.apache.zookeeper/zookeeper "3.5.6"] ; override zookeeper-clj, org.onyxplatform/onyx-kafka
                 [args4j "2.32"] ;org.onyxplatform/onyx-kafka > threatgrid/ctim
                 [com.stuartsierra/component "0.3.2"] ;org.onyxplatform/onyx-kafka internal override
                 [org.onyxplatform/onyx-kafka "0.14.5.0"]
                 ;; Notes on jackson-databind:
                 ;; - overrides org.onyxplatform/onyx-kafka and others
                 ;; - some 2.9.x versions of jackson-databind and earlier have known exploits
                 ;; - 2.10.2 is the same as cheshire's jackson-core dependency
                 [com.fasterxml.jackson.core/jackson-databind "2.10.2"]
                 [zookeeper-clj "0.9.4"]

                 ;; GraphQL
                 [base64-clj "0.1.1"]
                 [threatgrid/ring-graphql-ui "0.1.1"]
                 [com.graphql-java/graphql-java "9.7"]]

  :resource-paths ["resources" "doc"]
  :classpath ".:resources"
  :min-lein-version "2.9.1"
  :test-selectors ~(-> (slurp "dev-resources/circleci_test/config.clj")
                       read-string
                       :selectors)
  :filespecs [{:type :fn
               :fn (fn [_]
                     {:type :bytes :path "ctia-version.txt"
                      :bytes (str (:out (clojure.java.shell/sh
                                         "git" "log" "-n" "1" "--pretty=format:%H "))
                                  (:out (clojure.java.shell/sh
                                         "git" "symbolic-ref" "--short" "HEAD")))})}]

  
  :profiles {:dev {:dependencies [[puppetlabs/trapperkeeper ~trapperkeeper-version
                                   :classifier "test"]
                                  [puppetlabs/kitchensink ~trapperkeeper-version
                                   :classifier "test"]
                                  [org.clojure/test.check ~test-check-version]
                                  [com.gfredericks/test.chuck ~test-chuck-version]
                                  [clj-http-fake ~clj-http-fake-version]
                                  [prismatic/schema-generators ~schema-generators-version]
                                  [circleci/circleci.test "0.4.3"]
                                  [org.clojure/math.combinatorics "0.1.6"]
                                  [org.clojure/data.priority-map "1.0.0"]
                                  [org.clojure/tools.namespace "1.1.0"]]
                   :pedantic? :warn
                   :resource-paths ["test/resources"]
                   :source-paths ["dev"]}
             :jmx {:jvm-opts ["-Dcom.sun.management.jmxremote"
                              "-Dcom.sun.management.jmxremote.port=9010"
                              "-Dcom.sun.management.jmxremote.local.only=false"
                              "-Dcom.sun.management.jmxremote.authenticate=false"
                              "-Dcom.sun.management.jmxremote.ssl=false"]}
             :bench {:dependencies [[perforate ~perforate-version]
                                    [criterium "0.4.5"]
                                    [org.clojure/test.check ~test-check-version]
                                    [com.gfredericks/test.chuck ~test-chuck-version]
                                    [prismatic/schema-generators ~schema-generators-version]]
                     :source-paths ["src","test","benchmarks"]}
             :uberjar {:aot [ctia.main]
                       :main ctia.main
                       :uberjar-name "ctia.jar"
                       :uberjar-exclusions [#"ctia\.properties"]}
             :test {:jvm-opts ~(cond-> ["-Dlog.console.threshold=WARN"]
                                 ; we have 7.5GB RAM on Travis.
                                 ; docker reserves 4GB. here's how to customize it:
                                 ; - https://docs.travis-ci.com/user/enterprise/worker-configuration/#configuring-jobs-allowed-memory-usage
                                 ; this reserves 3GB jvm
                                 (System/getProperty "TRAVIS") (into ["-Xms3g"
                                                                      "-Xmx3g"])
                                 ; we have 7GB RAM on Actions
                                 ; - https://docs.github.com/en/free-pro-team@latest/actions/reference/specifications-for-github-hosted-runners#supported-runners-and-hardware-resources
                                 ; docker reserves an unknown amount of RAM.
                                 ; reserving 3GB for jvm -- this might need tweaking as we learn
                                 ; more about docker on actions.
                                 (System/getProperty "GITHUB_ACTIONS") (into ["-Xms3g"
                                                                              "-Xmx3g"]))
                    :dependencies [[clj-http-fake ~clj-http-fake-version]
                                   [com.gfredericks/test.chuck ~test-chuck-version]
                                   [org.clojure/test.check ~test-check-version]
                                   [prismatic/schema-generators ~schema-generators-version]]
                    :resource-paths ["test/resources"]}

             :prepush {:plugins [[yogsototh/lein-kibit "0.1.6-SNAPSHOT"]
                                 [lein-bikeshed "0.3.0"]]}}

  :ci {:pedantic? :abort
       :global-vars {*warn-on-reflection* true}}
  
  :perforate {:environments [{:name :actor
                              :namespaces [ctia.entity.actor-bench]}
                             {:name :campaign
                              :namespaces [ctia.entity.campaign-bench]}
                             {:name :bulk
                              :namespaces [ctia.bulk.routes-bench]}
                             {:name :migration
                              :namespaces [ctia.tasks.migrate-es-stores-bench]}]}
  ;; use `lein deps :plugins-tree` to inspect conflicts
  :plugins [[lein-shell "0.5.0"]
            [org.clojure/clojure ~clj-version] ;override perforate
            [perforate ~perforate-version]
            [reifyhealth/lein-git-down "0.3.5"]]
  :repl-options {:welcome (println
                            (clojure.string/join
                              "\n"
                              ["Welcome to CTIA!"
                               " (go)    => start or restart CTIA"
                               " (start) => start CTIA, if not already started"
                               " (stop)  => stop CTIA, if not already stopped"
                               " (current-app) => get current app, or nil"]))
                 ;10m
                 :repl-timeout 600000}
  :middleware [lein-git-down.plugin/inject-properties]
  ;; lein-git-down config
  :repositories [["public-github" {:url "git://github.com"}]
                 ["private-github" {:url "git://github.com" :protocol :ssh}]]
  ;; to simultaneously work on an upstream dependency and have
  ;; Travis pick up on it:
  ;; 1. add an entry mapping the upstream's maven coordinate to its dev GitHub repository
  ;;    in the :git-down map:
  ;;    eg., To work on ctim in the `frenchy64` fork:
  ;;         :git-down {threatgrid/ctim {:coordinates frenchy64/ctim}}
  ;; 2. change the upstream dependency's version to the relevant sha
  ;;    eg., [threatgrid/ctim "9acbc93333d630d9b9a0a9fc19981b0ba0ddec1c"]
  ;;

  ;; uncomment and change during dev
  #_:git-down #_{threatgrid/ctim {:coordinates frenchy64/ctim}
                 threatgrid/clj-momo {:coordinates frenchy64/clj-momo}
                 threatgrid/ring-jwt-middleware {:coordinates frenchy64/ring-jwt-middleware}}
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
            ["shell" "scripts/init-properties-for-docker.sh"]
            
            ; circleci.test
            ;"test" ["run" "-m" "circleci.test/dir" :project/test-paths]
            "split-test" ["trampoline"
                          "with-profile" "+test,+ci" ;https://github.com/circleci/circleci.test/issues/13
                          "run" "-m" "ctia.dev.split-tests/dir" :project/test-paths]
            "tests" ["with-profile" "+ci" "run" "-m" "circleci.test"]
            ;"retest" ["run" "-m" "circleci.test.retest"]
            })
