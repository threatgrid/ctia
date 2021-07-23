(def cheshire-version "5.10.0")
(def clj-http-fake-version "1.0.3")
(def clj-version "1.10.3")
(def metrics-clojure-version "2.10.0")
(def perforate-version "0.3.4")
(def ring-version "1.9.4")
(def schema-generators-version "0.1.3")
(def test-check-version "1.1.0")
(def test-chuck-version "0.2.11")
(def trapperkeeper-version "3.1.0")

;; TODO we could add -dev here when it works
(def base-ci-profiles "+test,+ci")
(def all-ci-profiles
  "All the permutations of CI profiles. This helps download dependencies
  for all build configurations on demand, to minimize the load we
  put on Maven repositories.

  By centralizing all build configurations here, we can use LEIN_OFFLINE=true
  when running tests to automatically catch errors in the dep caching logic.
  
  To add a new build, add an entry here and use CTIA_CI_PROFILES to select it."
  {:next-clojure (str base-ci-profiles ",+next-clojure")
   :uberjar "uberjar"
   :default base-ci-profiles})
(def ci-profiles
  (get all-ci-profiles
       (or (some-> (System/getenv "CTIA_CI_PROFILES")
                   not-empty
                   keyword)
           :default)))
(assert ci-profiles (pr-str (System/getenv "CTIA_CI_PROFILES")))

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
  :exclusions [org.slf4j/log4j-over-slf4j
               org.clojure/clojure
               ] ;; remove from trapperkeeper jars
  ;; use `lein pom; mvn dependency:tree -Dverbose -Dexcludes=org.clojure:clojure`
  ;; to inspect conflicts.

  :dependencies [[org.clojure/clojure ~clj-version]
                 [clj-time "0.15.2"]
                 [org.threeten/threeten-extra "1.2"]
                 [clojure.java-time "0.3.2"]
                 [org.clojure/core.async "1.3.618"]
                 [org.slf4j/slf4j-log4j12 "1.8.0-beta0"]
                 [org.clojure/core.memoize "1.0.236"]
                 [org.clojure/tools.logging "1.1.0"]
                 [org.clojure/tools.cli "1.0.206"]
                 [pandect "0.6.1"]
                 [org.clojure/math.combinatorics "0.1.6"]
                 [version-clj "2.0.1"]

                 ;; Trapperkeeper
                 [puppetlabs/trapperkeeper ~trapperkeeper-version]
                 [puppetlabs/kitchensink ~trapperkeeper-version]
                 [prismatic/plumbing "0.5.5"] ;; upgrade puppetlabs/trapperkeeper

                 ;; Schemas
                 [prismatic/schema "1.2.0"]
                 [metosin/schema-tools "0.12.2"]
                 [threatgrid/flanders "0.1.23"]
                 [threatgrid/ctim "1.1.10"]
                 [threatgrid/clj-momo "0.3.5"]
                 [threatgrid/ductile "0.4.2"]

                 [com.arohner/uri "0.1.2"]

                 ;; Web server
                 [metosin/compojure-api "1.1.13" ]
                 [ring-middleware-format "0.7.4"]
                 ;; optional ring-middleware-format dep (Note: ring-middleware-format is also a transitive dep for compojure-api)
                 ;; see: https://github.com/ngrunwald/ring-middleware-format/issues/74
                 [com.ibm.icu/icu4j "69.1"]
                 [metosin/ring-swagger "0.26.2"]
                 [metosin/ring-swagger-ui "3.24.3"]
                 [ring/ring-core ~ring-version] ;ring/ring-jetty-adapter > metosin/ring-swagger
                 [ring/ring-jetty-adapter ~ring-version]
                 [ring/ring-devel ~ring-version]
                 [ring-cors "0.1.13"]
                 [commons-codec "1.15"] ;threatgrid/ctim, threatgrid/clj-momo, clj-http > ring/ring-codec
                 [ring/ring-codec "1.1.3"]
                 [threatgrid/clj-jwt "0.3.1"]
                 [threatgrid/ring-turnstile-middleware "0.1.1"]
                 [threatgrid/ring-jwt-middleware "1.0.1"]
                 [scopula "0.1.4"]
                 [org.clojure/tools.reader "1.3.6"] ;; org.clojure/tools.namespace > ring-middleware-format

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
                 [com.google.guava/guava "30.1.1-jre"];org.onyxplatform/onyx-kafka > threatgrid/ctim
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
                 [com.graphql-java/graphql-java "9.7"]

                 ;; Front-end
                 [thheller/shadow-cljs "2.15.2"]
                 [com.cognitect/transit-clj "1.0.324"]
                 [org.clojure/spec.alpha "0.2.194"]
                 [org.clojure/core.specs.alpha "0.2.56"]
                 [thheller/shadow-undertow "0.1.0"]
                 [org.apache.httpcomponents/httpcore "4.4.14"]
                 [reagent "1.1.0"]
                 [re-frame "1.2.0"]
                 [day8.re-frame/http-fx "0.2.3"]
                 [cljs-ajax "0.8.3"]
                 [cljs-bean "1.7.0"]
                 [org.clojure/data.json "2.4.0"]
                 ]

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
                                  [org.clojure/data.priority-map "1.0.0"]]
                   :pedantic? :warn
                   :resource-paths ["test/resources"]
                   :source-paths ["dev"]}
             :ci {:pedantic? :abort
                  :global-vars {*warn-on-reflection* true}}
             :next-clojure {:dependencies [[org.clojure/clojure "1.11.0-alpha3"]]}
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
             :test {:jvm-opts ["-Dlog.console.threshold=WARN"]
                    :dependencies [[clj-http-fake ~clj-http-fake-version]
                                   [com.gfredericks/test.chuck ~test-chuck-version]
                                   [org.clojure/test.check ~test-check-version]
                                   [prismatic/schema-generators ~schema-generators-version]]
                    :resource-paths ["test/resources"]}

             :prepush {:plugins [[yogsototh/lein-kibit "0.1.6-SNAPSHOT"]
                                 [lein-bikeshed "0.3.0"]]}}

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
                 :repl-timeout 600000
                 :nrepl-middleware [shadow.cljs.devtools.server.nrepl/middleware]}
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
                          "with-profile" ~ci-profiles ;https://github.com/circleci/circleci.test/issues/13
                          "run" "-m" "ctia.dev.split-tests/dir" :project/test-paths]
            "tests" ["with-profile" ~ci-profiles "run" "-m" "circleci.test"]

            "ci-run-tests" ["with-profile" ~ci-profiles "do" "clean," "javac," "split-test" ":no-gen"]
            "cron-run-tests" ["with-profile" ~ci-profiles "do" "clean," "javac," "split-test" ":all"]
            "all-ci-profiles" ["shell" "echo" ~(pr-str all-ci-profiles)]
            ;; warm deps cache for all permutations of the build
            "warm-ci-deps" ["do"
                            ~(mapv (fn [p]
                                     ["with-profile" p ["do"
                                                        ["shell" "echo" (str "\n\nlein with-profile " p " ...")]
                                                        ["deps" ":tree"]
                                                        ["deps" ":plugin-tree"]]])
                                   (vals all-ci-profiles))]

            ;"retest" ["run" "-m" "circleci.test.retest"]
            })
