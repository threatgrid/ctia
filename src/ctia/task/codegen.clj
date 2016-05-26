(ns ctia.task.codegen
  ^{:doc "A task to generate swagger client libraries for a handful of languages"}
  (:require [ctia.init :refer [start-ctia!]]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [ctia.properties :refer [properties]]))

(def codegen-version "2.1.6")
(def codegen-repo "http://central.maven.org/maven2/io/swagger/swagger-codegen-cli/")
(def supported-languages ["clojure"
                          "java"
                          "haskell-servant"
                          "python"
                          "ruby"])

(def jar-uri (str codegen-repo
                  codegen-version
                  "/swagger-codegen-cli-"
                  codegen-version ".jar"))

(def local-jar-uri (str "/tmp/swagger-codegen-" codegen-version ".jar"))

(defn spec-uri []
  (let [port (get-in @properties [:ctia :http :port] 3000)]
    (str "http://localhost:" port "/swagger.json")))

(defn exec-command [& args]
  (when-let [err (:err (apply shell/sh args))]
    (println err)))

(defn setup []
  (println "starting CTIA...")
  (start-ctia! :join? false :silent? true)
  (when-not (.exists (io/file local-jar-uri))
    (do (println "downloading swagger-codegen" codegen-version "...")
        (exec-command "curl" "-o" local-jar-uri jar-uri))))

(defn generate-language [lang output-dir]
  (println "generating" lang "client...")
  (exec-command "java"
                "-jar"
                local-jar-uri
                "generate"
                "-i" (spec-uri)
                "-l" lang
                "--group-id" "cisco"
                "-o" (str (or output-dir "/tmp/ctia-client") "/" lang)))

(defn -main [output-dir]
  "invoke with lein run -m ctia.task.codegen <output-dir>"
  (setup)
  (doseq [lang supported-languages]
    (generate-language lang output-dir))

  (println "done")
  (System/exit 0))
