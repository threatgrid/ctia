(defproject ctia "0.1.0-SNAPSHOT"
  :description "Cisco Threat Intelligence API"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [clj-time "0.9.0"] ; required due to bug in lein-ring
                 [metosin/schema-tools "0.7.0"]
                 [com.rpl/specter "0.9.2"]
                 [org.clojure/core.async "0.2.374"]
                 [clj-http "2.0.1"]
                 [org.slf4j/slf4j-log4j12 "1.7.21"]
                 [org.clojure/core.memoize "0.5.8"]
                 [org.clojure/tools.logging "0.3.1"]
                 [leiningen-core "2.6.1"] ;; For accessing project configuration

                 ;; Web server
                 [metosin/compojure-api "1.0.0"]
                 [ring/ring-jetty-adapter "1.4.0"]
                 [javax.servlet/servlet-api "2.5"]
                 [ring-middleware-format "0.7.0"]
                 [ring/ring-devel "1.4.0"]

                 ;; nREPL server
                 [org.clojure/tools.nrepl "0.2.12"]
                 [cider/cider-nrepl "0.11.0"]

                 ;; Database
                 [clojurewerkz/elastisch "2.2.1"]
                 [korma "0.4.2"]
                 [org.clojure/java.jdbc "0.3.7"] ; specified by korma

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
                   :sql-store :sql-store
                   :es-store :es-store
                   :es-producer #(or (:es-producer %)
                                     (:es-producer-filtered-alias %)
                                     (:es-producer-aliased-index %))

                   :default #(not (or (:es-store %)
                                      (:es-producer %)
                                      (:es-producer-filtered-alias %)
                                      (:es-producer-aliased-index %)
                                      (:integration %)
                                      (:regression %)))
                   :integration #(or (:es-store %)
                                     (:integation %)
                                     (:es-producer %)
                                     (:es-producer-filtered-alias %)
                                     (:es-producer-aliased-index %))}

  :java-source-paths ["hooks/ctia"]
  :javac-options  ["-proc:none"] ;; remove a warning
  :profiles {:dev {:dependencies [[cheshire "5.5.0"]
                                  [com.h2database/h2 "1.4.191"]
                                  [org.clojure/test.check "0.9.0"]]
                   :plugins [[lein-ring "0.9.6"]]
                   :resource-paths ["model"
                                    "test/resources"]}
             :ci {:jvm-opts ["-XX:MaxPermSize=256m"]}
             :test {:dependencies [[cheshire "5.5.0"]
                                   [com.h2database/h2 "1.4.191"]
                                   [org.clojure/test.check "0.9.0"]]
                    :hooks-classes {:before-create [ctia.hook.AutoLoadedJar1
                                                    hook-example.core/HookExample1
                                                    ctia.hook.AutoLoadedJar2
                                                    hook-example.core/HookExample2]}
                    :java-source-paths ["hooks/ctia" "test/java"]
                    :plugins [[lein-ring "0.9.6"]]
                    :resource-paths ["model"
                                     "test/resources"
                                     "test/resources/hooks/JarHook.jar"
                                     "test/resources/hooks/AutoloadHook.jar"
                                     "test/resources/hooks/hook-example-0.1.0-SNAPSHOT.jar"]}})
