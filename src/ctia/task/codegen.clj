(ns ctia.task.codegen
  (:require [clojure.java
             [io :as io]
             [shell :as shell]]
            [ctia.init :refer [start-ctia!]]
            [puppetlabs.trapperkeeper.app :as app]))

;; swagger codegen package
(def codegen-version "2.2.3")
(def codegen-repo "http://central.maven.org/maven2/io/swagger/swagger-codegen-cli/")
(def jar-uri (str codegen-repo
                  codegen-version
                  "/swagger-codegen-cli-"
                  codegen-version ".jar"))

(def local-jar-uri (str "/tmp/swagger-codegen-" codegen-version ".jar"))

(def artifact-id "ctia-client")
(def description "a client library for CTIA")
(def artifact-version "0.1")
(def base-namespace "ctia-client")

(def langs
  {:clojure {:projectDescription description
             :projectName artifact-id
             :projectVersion artifact-version
             :baseNamespace base-namespace}
   :python {:packageName artifact-id
            :packageVersion artifact-version}
   :haskell-servant {:apiVersion artifact-version
                     :title "CTIA Client"
                     :titleLower "ctia client"
                     :package "CTIAClient"
                     :baseNamespace "CTIAClient"
                     :modelpackage "CTIAClient"
                     :artifact-id "CTIAClient"}
   :ruby {:gemName artifact-id
          :gemVersion artifact-version
          :gemHomepage "http://github.com/threatgrid/ctia"
          :gemSummary description
          :gemDescription description
          :gemAuthor "Cisco Security Business Group -- Advanced Threat"
          :gemAuthorEmail "cisco-intel-api-support@cisco.com"}})

(defn spec-uri
  "compose the full path of the swagger spec to generate from"
  [get-port]
  (let [port (get-port)]
    (str "http://localhost:" port "/swagger.json")))

(defn exec-command
  "execute a shell command and output any :err"
  [& args]
  (when-let [err (:err (apply shell/sh args))]
    (println err)))

(defn setup
  "start CTIA and download swagger-codegen if needed"
  []
  (println "starting CTIA...")
  (let [app (start-ctia!)]
    (when-not (.exists (io/file local-jar-uri))
      (println "downloading swagger-codegen" codegen-version "...")
      (exec-command "curl" "-o" local-jar-uri jar-uri))
    app))

(defn base-command
  "base command for all languages"
  [lang output-dir get-port]
  ["java"
   "-jar" local-jar-uri
   "generate"
   "-i" (spec-uri get-port)
   "-l" (name lang)
   "--group-id" "cisco"
   "-o" (str (or output-dir "/tmp/ctia-client") "/" (name lang))
   "--api-package" (get-in langs [lang :baseNamespace] artifact-id)
   "--model-package" (get-in langs [lang :modelpackage] "model")
   "--artifact-id" (get-in langs [lang :artifact-id] artifact-id)
   "--artifact-version" artifact-version])

(defn props->additional-properties [props]
  ["--additional-properties"
   (->> props
        (map (fn [[k v]]
               (str (name k) "=" v)))
        (interpose ",")
        (apply str))])

(defn generate-language
  "generate code for one language"
  [lang props output-dir get-port]

  (println "generating" lang "client...")

  (let [base (base-command lang output-dir get-port)
        additional (props->additional-properties props)
        full-command (into base additional)]
    (apply exec-command full-command)))

(defn -main
  "invoke with lein run -m ctia.task.codegen <output-dir>"
  [output-dir]
  (try
    (let [app (setup)
          {{:keys [get-port]} :CTIAHTTPServerService} (app/service-graph app)]
      (doseq [[lang props] langs]
        (generate-language lang props output-dir get-port))

      (println "done")
      (System/exit 0))
    (finally
      (println "unknown error")
      (System/exit 1))))
