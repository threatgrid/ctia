(defproject ctia "0.1.0-SNAPSHOT"
  :description "Cisco Threat Intelligence API"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}

  :jvm-opts [ "-Xmx4g" ;; On some OSX VMs, this is needed to increase available memory
             "-Djava.awt.headless=true"
             "-Dlog.console.threshold=INFO"
             "-server"]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clj-time "0.9.0"] ; required due to bug in lein-ring
                 [com.rpl/specter "0.9.2"]
                 [org.clojure/core.async "0.2.374"]
                 [clj-http "2.0.1"]
                 [org.slf4j/slf4j-log4j12 "1.7.21"]
                 [org.clojure/core.memoize "0.5.8"]
                 [org.clojure/tools.logging "0.3.1"]
                 [leiningen-core "2.6.1"] ;; For accessing project configuration
                 [com.taoensso/carmine "2.12.2"]
                 [org.clojure/tools.cli "0.3.5"]
                 [pandect "0.6.0"]
                 [org.clojure/data.xml "0.0.8"]

                 ;; Schemas
                 [prismatic/schema "1.0.5"]
                 [metosin/schema-tools "0.7.0"
                  :exclusions [prismatic/schema]]
                 [threatgrid/ctim "0.1.5"]

                 ;; Web server
                 [metosin/compojure-api "1.0.0"]
                 [ring/ring-jetty-adapter "1.4.0"]
                 [javax.servlet/servlet-api "2.5"]
                 [ring/ring-devel "1.4.0"]
                 [ring/ring-codec "1.0.0"]

                 ;; nREPL server
                 [org.clojure/tools.nrepl "0.2.12"]
                 [cider/cider-nrepl "0.11.0"]

                 ;; Database
                 [clojurewerkz/elastisch "3.0.0-beta1"]

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
                 [hiccup "1.0.5"]]

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

  :profiles {:dev {:dependencies [[cheshire "5.6.1"]
                                  [org.clojure/test.check "0.9.0"]
                                  [com.gfredericks/test.chuck "0.2.6"]
                                  [perforate "0.3.4"]
                                  [prismatic/schema-generators "0.1.0"
                                   :exclusions [prismatic/schema]]]
                   :resource-paths ["test/resources"]}
             :jmx {:jvm-opts ["-Dcom.sun.management.jmxremote"
                              "-Dcom.sun.management.jmxremote.port=9010"
                              "-Dcom.sun.management.jmxremote.local.only=false"
                              "-Dcom.sun.management.jmxremote.authenticate=false"
                              "-Dcom.sun.management.jmxremote.ssl=false"]}
             :bench {:dependencies [[cheshire "5.6.1"]
                                    [perforate "0.3.4"]
                                    [org.clojure/test.check "0.9.0"]
                                    [com.gfredericks/test.chuck "0.2.6"]
                                    [prismatic/schema-generators "0.1.0"]]}
             :test {:jvm-opts ["-Dlog.console.threshold=WARN"]
                    :dependencies [[cheshire "5.6.1"]
                                   [org.clojure/test.check "0.9.0"]
                                   [com.gfredericks/test.chuck "0.2.6"]
                                   [prismatic/schema-generators "0.1.0"]]
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
                              :namespaces [ctia.http.routes.campaign-bench]}]}
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
