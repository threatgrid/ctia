#!/usr/bin/env bb

;; Devs: run `bb nrepl` at project root start an nrepl server

(require '[babashka.process :refer [process check]]
         '[cheshire.core :as json]
         '[clojure.string :as str]
         '[clojure.pprint :as pp])

(def uberjar-file-relative-to-scanning-dir "../../target/ctia.jar")
;; repo root relative
(def scanning-directory "tmp/unzipped-uberjar")
;; repo root relative
(def config-file "scripts/do_not_edit-uberjar_trojansourcedetector.json")

(def uberjar-exclusions
  [
   "META-INF/native/linux64/liblmdbjni.so"
   "com/google/common/base/CharMatcher$Invisible.class"
   "com/google/protobuf/DescriptorProtos$SourceCodeInfo$Location$Builder.class"
   "com/ibm/icu/impl/data/icudt65b/brkitr/*.dict"
   "com/ibm/icu/impl/data/icudt65b/*.res"
   "com/ibm/icu/impl/data/icudt65b/*/*.res"
   "com/ibm/icu/impl/data/icudt65b/unames.icu"
   "externs.zip"
   "goog/demos/emoji/*.gif"
   "goog/demos/emoji/*.png"
   "goog/images/*.gif"
   "goog/images/*.png"
   "graphql/parser/antlr/GraphqlLexer.class"
   "img/*.png"
   "org/apache/commons/codec/language/bm/gen_rules_arabic.txt"
   "org/apache/curator/shaded/com/google/common/base/CharMatcher$Invisible.class"
   "org/bouncycastle/crypto/engines/AESFastEngine.class"
   "org/xerial/snappy/native/Linux/android-arm/libsnappyjava.so"
   "org/xerial/snappy/native/Linux/ppc64le/libsnappyjava.so"
   "org/xerial/snappy/native/Windows/x86/snappyjava.dll"

   ;; :/
   "goog/i18n/datetimepatternsext.js"
   "goog/i18n/datetimesymbols.js"
   "goog/i18n/datetimesymbolsext.js"
   "goog/labs/i18n/listsymbols.js"
   "goog/labs/i18n/listsymbolsext.js"
   "goog/i18n/datetimepatterns.js"
   "goog/format/internationalizedemailaddress.js"
   "swagger-ui/swagger-ui-bundle.js"
   ])

(defn regen-config []
  (spit config-file
        (str
          (json/generate-string
            (sorted-map
              "directory" scanning-directory
              "exclude" uberjar-exclusions)
            {:pretty (json/create-pretty-printer
                       (assoc json/default-pretty-print-options
                              :indent-arrays? true))})
          \newline)))

(defn run-trojansourcedetector []
  {:post [(#{0 1} %)]}
  (regen-config)
  (let [{:keys [exit]} @(process ["trojansourcedetector"
                                  "-config"
                                  config-file]
                                 {:inherit true})]
    (if (zero? exit)
      0
      (do (println)
          (println "uberjar scan failed with exit code" exit)
          (println)
          (println "Please review the failures and add valid exclusions to the `uberjar-exclusions`")
          (println "var in `./scripts/uberjar-trojan-scan.clj`.")
          1))))

(defn unzip-uberjar []
  (let [_ (check (process ["rm" "-rf" scanning-directory] {:inherit true}))
        _ (check (process ["mkdir" "-p" scanning-directory] {:inherit true}))
        ;; FIXME
        ;; erm, there's something wrong with ctia's META-INF/license directory,
        ;; it cannot be unzipped with jar.
        ;;   java.io.IOException: META-INF/license : could not create directory
        ;;   at jdk.jartool/sun.tools.jar.Main.extractFile(Main.java:1414)
        ;;   at jdk.jartool/sun.tools.jar.Main.extract(Main.java:1348)
        ;;   at jdk.jartool/sun.tools.jar.Main.run(Main.java:381)
        ;;   at jdk.jartool/sun.tools.jar.Main.main(Main.java:1665)
        ;; related? https://bugs.java.com/bugdatabase/view_bug.do?bug_id=4067481
        ;; workaround: cross our fingers and use unzip instead.
        #__ #_(check (process ["jar" "xf" uberjar-file-relative-to-scanning-dir]
                              ["unzip"
                               ;; quiet and allow overwriting files
                               "-qo"
                               uberjar-file-relative-to-scanning-dir]
                              {:dir scanning-directory
                               :inherit true}))
        ;; hack around these errors:
        ;;   checkdir error:  META-INF/license exists but is not directory
        ;;         unable to process META-INF/license/LICENSE.base64.txt.
        ;; approach:
        ;; - ignore unzip errors
        ;; - use `ls` to check that something was unzipped
        _ @(process ["unzip"
                     ;; quiet and allow overwriting files
                     "-qo"
                     uberjar-file-relative-to-scanning-dir]
                    {:dir scanning-directory
                     :inherit true})
        _ (check (process ["ls" "ctia/main.clj"]
                          {:dir scanning-directory
                           :inherit true}))
        ]))

(comment
  (unzip-uberjar)
  (run-trojansourcedetector)
  )

(defn -main [& args]
  (assert (empty? args))
  (unzip-uberjar)
  (System/exit (run-trojansourcedetector)))

(comment
  (clojure.repl/pst)
  (-main))

(when (= *file* (System/getProperty "babashka.file")) (-main))
