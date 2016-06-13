(ns ctia.task.codegen
  ^{:doc "A task to generate swagger client libraries for a handful of languages"}
  (:require [ctia.init :refer [start-ctia!]]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [ctia.properties :refer [properties]]
            [clojure.string :as str]))

;; swagger codegen package
(def codegen-version "2.1.6")
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
  []
  (let [port (get-in @properties [:ctia :http :port] 3000)]
    (str "http://localhost:" port "/swagger.json")))

(defn exec-command
  "execute a shell command and output any :err"
  [& args]
  (when-let [err (:err (apply shell/sh args))]
    (println err)))

(defn setup []
  "start CTIA and download swagger-codegen if needed"
  (println "starting CTIA...")
  (start-ctia! :join? false)
  (when-not (.exists (io/file local-jar-uri))
    (println "downloading swagger-codegen" codegen-version "...")
    (exec-command "curl" "-o" local-jar-uri jar-uri)))

(defn base-command
  "base command for all languages"
  [lang output-dir]
  ["java"
   "-jar" local-jar-uri
   "generate"
   "-i" (spec-uri)
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
  [lang props output-dir]

  (println "generating" lang "client...")

  (let [base (base-command lang output-dir)
        additional (props->additional-properties props)
        full-command (into base additional)]
    (apply exec-command full-command)))

(defn -main [output-dir]
  "invoke with lein run -m ctia.task.codegen <output-dir>"
  (setup)
  (doseq [[lang props] langs]
    (generate-language lang props output-dir))

  (println "done")
  (System/exit 0))
