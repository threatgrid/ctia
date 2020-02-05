(def cheshire-version "5.10.0")
(def clj-http-fake-version "1.0.3")
(def clj-version "1.10.1")
(def metrics-clojure-version "2.10.0")
(def perforate-version "0.3.4")
(def ring-version "1.8.0")
(def schema-generators-version "0.1.3")
(def test-check-version "0.10.0")
(def test-chuck-version "0.2.10")

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
  ; use `lein pom; mvn dependency:tree -Dverbose -Dexcludes=org.clojure:clojure`
  ; to inspect conflicts.
  :dependencies [[org.clojure/clojure ~clj-version]
                 [clj-time "0.15.2"]
                 [org.clojure/core.async "0.7.559"]
                 [org.slf4j/slf4j-log4j12 "1.8.0-beta0"]
                 [org.clojure/core.memoize "0.8.2"]
                 [org.clojure/tools.logging "0.5.0"]
                 [org.clojure/tools.cli "0.4.2"]
                 [pandect "0.6.1"]

                 ;; Schemas
                 [prismatic/schema "1.1.12"]
                 [metosin/schema-tools "0.12.2"]
                 [threatgrid/flanders "0.1.23-20200204.180031-3"] ;sha: f92c65bb811e97cc0b96443e46474ba8caa1e1cc

                  
                 [threatgrid/ctim "1.0.16-20200205.182529-5"] ;sha: 121b661f9638d6720c2ac22f2bdd125383abdc8a
                 [threatgrid/clj-momo "0.3.4-20200204.172456-2"] ;sha: 296203d2bc07e0063103e4bc1cc0c921284db927

                 [com.arohner/uri "0.1.2"]

                 ;; Web server
                 [metosin/compojure-api "1.1.13" ]
                 ; optional dep for compojure-api's dep ring-middleware-format
                 ; see: https://github.com/ngrunwald/ring-middleware-format/issues/74
                 [com.ibm.icu/icu4j "65.1"]
                 [metosin/ring-swagger "0.26.2"]
                 [metosin/ring-swagger-ui "3.24.3"]
                 [ring/ring-core ~ring-version] ;ring/ring-jetty-adapter > metosin/ring-swagger
                 [ring/ring-jetty-adapter ~ring-version]
                 [ring/ring-devel ~ring-version]
                 [ring-cors "0.1.13"]
                 [commons-codec "1.12"] ;threatgrid/ctim, threatgrid/clj-momo, clj-http > ring/ring-codec
                 [ring/ring-codec "1.1.2"]
                 [yogsototh/clj-jwt "0.2.1"]
                 [threatgrid/ring-turnstile-middleware "0.1.1-20200203.182733-1"];sha: 6bef68919a0038d55721c44a84c03e7827a26f42
                 [threatgrid/ring-jwt-middleware "1.0.0"]
                 [scopula "0.1.4"]

                 ;; clients
                 [clj-http "3.9.1"] ;TODO bump clj-http with https://github.com/dakrone/clj-http/pull/532
                                    ; Note: clj-http 3.10.0 breaks the ctia unit tests, in the same way as https://github.com/dakrone/clj-http/issues/489
                 [com.taoensso/carmine "2.19.1" #_"2.20.0-RC1"]

                 ;; Metrics
                 [metrics-clojure ~metrics-clojure-version]
                 [metrics-clojure-jvm ~metrics-clojure-version]
                 [metrics-clojure-ring ~metrics-clojure-version]
                 [metrics-clojure-riemann ~metrics-clojure-version]
                 [clout "2.2.1"]
                 [slugger "1.0.1"]
                 [com.google.guava/guava "20.0"];org.onyxplatform/onyx-kafka > threatgrid/ctim
                 [io.netty/netty "3.10.6.Final"];org.onyxplatform/onyx-kafka > metrics-clojure-riemann, zookeeper-clj
                 [io.netty/netty-codec "4.1.42.Final"] ;org.apache.zookeeper/zookeeper > riemann-clojure-client
                 [io.netty/netty-resolver "4.1.42.Final"] ;riemann-clojure-client > org.apache.zookeeper/zookeeper
                 [com.google.protobuf/protobuf-java "3.11.1"] ;riemann-clojure-client > threatgrid:ctim, metrics-clojure-riemann, org.onyxplatform/onyx-kafka
                 [riemann-clojure-client "0.5.1"]
                 ; https://stackoverflow.com/a/43574427
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
                 [zookeeper-clj "0.9.4"]

                 ;; GraphQL
                 [base64-clj "0.1.1"]
                 [threatgrid/ring-graphql-ui "0.1.1"]
                 [com.graphql-java/graphql-java "9.7"]]

  :resource-paths ["resources" "doc"]
  :aot [ctia.main]
  :main ctia.main
  :classpath ".:resources"
  :uberjar-name "ctia.jar"
  :uberjar-exclusions [#"ctia\.properties"]
  :min-lein-version "2.9.1"
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
  :filespecs [{:type :fn
               :fn (fn [_]
                     {:type :bytes :path "ctia-version.txt"
                      :bytes (str (:out (clojure.java.shell/sh
                                         "git" "log" "-n" "1" "--pretty=format:%H "))
                                  (:out (clojure.java.shell/sh
                                         "git" "symbolic-ref" "--short" "HEAD")))})}]

  :global-vars {*warn-on-reflection* true}
  :profiles {:dev {:dependencies [[cheshire ~cheshire-version]
                                  [org.clojure/test.check ~test-check-version]
                                  [com.gfredericks/test.chuck ~test-chuck-version]
                                  [clj-http-fake ~clj-http-fake-version]
                                  [prismatic/schema-generators ~schema-generators-version]]
                   :pedantic? :warn

                   :resource-paths ["test/resources"]}
             :jmx {:jvm-opts ["-Dcom.sun.management.jmxremote"
                              "-Dcom.sun.management.jmxremote.port=9010"
                              "-Dcom.sun.management.jmxremote.local.only=false"
                              "-Dcom.sun.management.jmxremote.authenticate=false"
                              "-Dcom.sun.management.jmxremote.ssl=false"]}
             :bench {:dependencies [[cheshire ~cheshire-version]
                                    [perforate ~perforate-version]
                                    [criterium "0.4.5"]
                                    [org.clojure/test.check ~test-check-version]
                                    [com.gfredericks/test.chuck ~test-chuck-version]
                                    [prismatic/schema-generators ~schema-generators-version]]
                     :source-paths ["src","test","benchmarks"]}
             :test {:jvm-opts ["-Dlog.console.threshold=WARN"]
                    :dependencies [[cheshire ~cheshire-version]
                                   [clj-http-fake ~clj-http-fake-version]
                                   [com.gfredericks/test.chuck ~test-chuck-version]
                                   [org.clojure/test.check ~test-check-version]
                                   [prismatic/schema-generators ~schema-generators-version]]
                    :pedantic? :abort
                    :resource-paths ["test/resources"]}

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
                              :namespaces [ctia.tasks.migrate-es-stores-bench]}]}
  ; use `lein deps :plugins-tree` to inspect conflicts
  :plugins [[lein-shell "0.5.0"]
            [org.clojure/clojure ~clj-version] ;override perforate
            [perforate ~perforate-version]]
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
