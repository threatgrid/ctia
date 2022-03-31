(load-file "config-src/project_config.clj")
(load-file "scripts/gen_deps_edn.clj") ;; assumes project-config ns is already loaded
(gen-deps-edn/-main)

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
(defproject #_project-config/ctia-jar-coords ctia "1.1.1-SNAPSHOT"
  :description "Cisco Threat Intelligence API"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}

  :jvm-opts ~project-config/global-jvm-opts
  :exclusions ~project-config/global-exclusions
  ;; use `lein pom; mvn dependency:tree -Dverbose -Dexcludes=org.clojure:clojure`
  ;; to inspect conflicts.
  :dependencies ~project-config/dependencies
  :resource-paths ~project-config/resource-paths
  :source-paths ~project-config/source-paths
  :test-paths ~project-config/test-source-paths
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


  :profiles {:dev {:dependencies ~project-config/dev-dependencies
                   :pedantic? :warn
                   :source-paths ~project-config/dev-source-paths}
             :ci {:pedantic? :abort
                  :global-vars {*warn-on-reflection* true}
                  :jvm-opts [;; actually print stack traces instead of useless
                             ;; "Full report at: /tmp/clojure-8187773283812483853.edn"
                             "-Dclojure.main.report=stderr"]}
             :next-clojure {:dependencies [[org.clojure/clojure ~project-config/next-clojure-version]]}
             :jmx {:jvm-opts ["-Dcom.sun.management.jmxremote"
                              "-Dcom.sun.management.jmxremote.port=9010"
                              "-Dcom.sun.management.jmxremote.local.only=false"
                              "-Dcom.sun.management.jmxremote.authenticate=false"
                              "-Dcom.sun.management.jmxremote.ssl=false"]}
             :bench {:dependencies [[perforate ~project-config/perforate-version]
                                    [criterium "0.4.5"]
                                    [org.clojure/test.check ~project-config/test-check-version]
                                    [com.gfredericks/test.chuck ~project-config/test-chuck-version]
                                    [prismatic/schema-generators ~project-config/schema-generators-version]]
                     :source-paths ["src","test","benchmarks"]}
             :uberjar {:aot [~project-config/main-ns]
                       :main ~project-config/main-ns
                       :uberjar-name ~project-config/uberjar-name
                       :uberjar-exclusions [#"ctia\.properties"]}
             :test {:dependencies ~project-config/test-dependencies
                    :resource-paths ~project-config/test-resource-paths}

             :prepush {:plugins [[yogsototh/lein-kibit "0.1.6-SNAPSHOT"]
                                 [lein-bikeshed "0.3.0"]]}
             :es5 {:jvm-opts ["-Dctia.store.es.default.port=9205"
                              "-Dctia.store.es.default.version=5"
                              "-Dctia.test.es-versions=[5]"]}
             :es7 {:jvm-opts ["-Dctia.store.es.default.port=9207"
                              "-Dctia.store.es.default.version=7"
                              "-Dctia.test.es-versions=[7]"]}}

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
            [org.clojure/clojure ~project-config/clj-version] ;override perforate
            [perforate ~project-config/perforate-version]
            [reifyhealth/lein-git-down "0.3.5"]]
  :repl-options {:welcome (println
                            (clojure.string/join
                              "\n"
                              ["Welcome to CTIA!"
                               " (go)    / (go7)    => (re)start CTIA (ES5/ES7)"
                               " (refresh)          => (re)load all code"
                               " (reset) / (reset7) => refresh, then (re)start CTIA (ES5/ES7)"
                               " (start) / (start7) => start CTIA (ES5/ES7)"
                               " (stop)             => stop CTIA"
                               " (current-app)      => get current app, or nil"]))
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
  ;; To work on the library locally without restarting the REPL, you can use lein checkouts.
  ;; 1. $ mkdir checkouts
  ;; 2. $ cd checkouts
  ;; 3. $ ln -s ../ctim
  ;; 4. Use `user/reset` to automatically reset checkouts
  ;;    - if this does not work, remove "checkouts" from `set-refresh-dirs` in dev/user.clj
  ;;      or replace with something more specific, eg., "checkouts/ctim/src"

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
