(ns project-config)

(def cheshire-version "5.10.2")
(def clj-http-fake-version "1.0.3")
(def clj-version "1.10.1")
(def metrics-clojure-version "2.10.0")
(def netty-version "4.1.75.Final")
(def perforate-version "0.3.4")
(def ring-version "1.8.0")
(def schema-generators-version "0.1.3")
(def test-check-version "1.1.0")
(def test-chuck-version "0.2.13")
(def trapperkeeper-version "3.1.0")
(def next-clojure-version "1.11.0-rc1")

(def source-paths ["src"])
(def dev-source-paths ["dev"])
(def test-source-paths ["test"])
(def test-resource-paths ["test-resources"])
(def resource-paths ["resources" "doc"])
(def global-jvm-opts
  ["-Djava.awt.headless=true"
   "-Dlog.console.threshold=INFO"
   "-server"])
(def global-exclusions
  `[io.netty/netty ;; moved to io.netty/netty-all
    org.slf4j/slf4j-log4j12
    org.slf4j/slf4j-nop]) ;; Removed in favor of logback
(def dependencies
  `[[org.clojure/clojure ~project-config/clj-version]
    [clj-time/clj-time "0.15.2"]
    [org.threeten/threeten-extra "1.2"]
    [clojure.java-time/clojure.java-time "0.3.2"]
    [org.clojure/core.async "1.0.567"]
    [org.clojure/core.memoize "1.0.236"]
    [org.clojure/tools.logging "1.1.0"]
    [org.clojure/tools.cli "1.0.194"]
    [pandect/pandect "0.6.1"]
    [org.clojure/math.combinatorics "0.1.6"]
    [version-clj/version-clj "2.0.1"]

    ;; Trapperkeeper
    [puppetlabs/trapperkeeper ~project-config/trapperkeeper-version]
    [puppetlabs/kitchensink ~project-config/trapperkeeper-version]
    [prismatic/plumbing "0.5.5"] ;; upgrade puppetlabs/trapperkeeper

    ;; Schemas
    [prismatic/schema "1.2.0"]
    [metosin/schema-tools "0.12.2"]
    [threatgrid/flanders "0.1.23"]
    [threatgrid/ctim "1.1.11"]
    [instaparse/instaparse "1.4.10"] ;; com.gfredericks/test.chuck > threatgrid/ctim
    [threatgrid/clj-momo "0.3.5"]
    [threatgrid/ductile "0.4.2"]

    [com.arohner/uri "0.1.2"]

    ;; Web server
    [metosin/compojure-api "1.1.13" ]
    [ring-middleware-format/ring-middleware-format "0.7.4"]
    ;; optional ring-middleware-format dep (Note: ring-middleware-format is also a transitive dep for compojure-api)
    ;; see: https://github.com/ngrunwald/ring-middleware-format/issues/74
    [com.ibm.icu/icu4j "65.1"]
    [metosin/ring-swagger "0.26.2"]
    [metosin/ring-swagger-ui "3.24.3"]
    [ring/ring-core ~project-config/ring-version] ;ring/ring-jetty-adapter > metosin/ring-swagger
    [ring/ring-jetty-adapter ~project-config/ring-version]
    [ring/ring-devel ~project-config/ring-version]
    [ring-cors/ring-cors "0.1.13"]
    [commons-codec/commons-codec "1.12"] ;threatgrid/ctim, threatgrid/clj-momo, clj-http > ring/ring-codec
    [ring/ring-codec "1.1.2"]
    [threatgrid/clj-jwt "0.3.1"]
    [threatgrid/ring-turnstile-middleware "0.1.1"]
    [threatgrid/ring-jwt-middleware "1.0.1"]
    [scopula/scopula "0.1.4"]
    [org.clojure/tools.reader "1.3.4"] ;; org.clojure/tools.namespace > ring-middleware-format

    ;; clients
    [clj-http/clj-http "3.10.1"]
    [com.taoensso/carmine "2.19.1" #_"2.20.0-RC1"]
    [cheshire/cheshire ~project-config/cheshire-version] ;; upgrade threatgrid/ring-jwt-middleware, puppetlabs/kitchensink (+ a dozen others)

    ;; Metrics
    [metrics-clojure/metrics-clojure ~project-config/metrics-clojure-version]
    [metrics-clojure-jvm/metrics-clojure-jvm ~project-config/metrics-clojure-version]
    [metrics-clojure-ring/metrics-clojure-ring ~project-config/metrics-clojure-version]
    [clout/clout "2.2.1"]
    [slugger/slugger "1.0.1"]
    [com.google.guava/guava "31.0-jre"];bump org.onyxplatform/onyx-kafka, threatgrid/ctim
    [io.netty/netty-all ~project-config/netty-version];bump org.onyxplatform/onyx-kafka, metrics-clojure-riemann, zookeeper-clj
    [io.netty/netty-codec ~project-config/netty-version] ;bump org.apache.zookeeper/zookeeper, riemann-clojure-client
    [io.netty/netty-resolver ~project-config/netty-version] ;bump riemann-clojure-client, org.apache.zookeeper/zookeeper
    [com.google.protobuf/protobuf-java "3.19.4"] ;bump riemann-clojure-client, threatgrid:ctim, metrics-clojure-riemann, org.onyxplatform/onyx-kafka
    [riemann-clojure-client/riemann-clojure-client "0.5.1"]
    ;; https://stackoverflow.com/a/43574427
    [jakarta.xml.bind/jakarta.xml.bind-api "2.3.2"]

    ;; Docs
    [markdown-clj/markdown-clj "1.10.1"]
    [hiccup/hiccup "2.0.0-alpha2"]

    ;; Encryption
    [lock-key/lock-key "1.5.0"]

    ;; Hooks
    [threatgrid/redismq "0.1.1"]

    [org.apache.zookeeper/zookeeper "3.5.6"] ; override zookeeper-clj, org.onyxplatform/onyx-kafka
    [args4j/args4j "2.32"] ;org.onyxplatform/onyx-kafka > threatgrid/ctim
    [com.stuartsierra/component "0.3.2"] ;org.onyxplatform/onyx-kafka internal override
    [org.onyxplatform/onyx-kafka "0.14.5.0"]
    ;; Notes on jackson-databind:
    ;; - overrides org.onyxplatform/onyx-kafka and others
    ;; - some 2.9.x versions of jackson-databind and earlier have known exploits
    ;; - 2.12.4 is the same as cheshire's jackson-core dependency
    [com.fasterxml.jackson.core/jackson-databind "2.12.4"]
    [zookeeper-clj/zookeeper-clj "0.9.4"]

    ;; GraphQL
    [base64-clj/base64-clj "0.1.1"]
    [threatgrid/ring-graphql-ui "0.1.1"]
    [com.graphql-java/graphql-java "9.7"]

    ;; Logging
    [org.slf4j/log4j-over-slf4j "1.7.20"]])
(def dev-dependencies
  `[[puppetlabs/trapperkeeper ~project-config/trapperkeeper-version
     :classifier "test"]
    [puppetlabs/kitchensink ~project-config/trapperkeeper-version
     :classifier "test"]
    [org.clojure/test.check ~project-config/test-check-version]
    [com.gfredericks/test.chuck ~project-config/test-chuck-version]
    [clj-http-fake/clj-http-fake ~project-config/clj-http-fake-version]
    [prismatic/schema-generators ~project-config/schema-generators-version]
    [circleci/circleci.test "0.4.3"]
    [org.clojure/math.combinatorics "0.1.6"]
    [org.clojure/data.priority-map "1.0.0"]
    [org.clojure/tools.namespace "1.1.0"]])
(def test-dependencies
  `[[clj-http-fake ~project-config/clj-http-fake-version]
    [com.gfredericks/test.chuck ~project-config/test-chuck-version]
    [org.clojure/test.check ~project-config/test-check-version]
    [prismatic/schema-generators ~project-config/schema-generators-version]])
