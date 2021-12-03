#!/usr/bin/env bb

;; Devs: run `bb nrepl` at project root start an nrepl server

;; https://github.com/haveyoudebuggedit/trojansourcedetector#configuration

(require '[cheshire.core :as json])

(defn gen-edn-config [base-config excluded-file-types excluded-directories]
  (let [explode-extension (fn [extension]
                            (take 15
                                  (iterate (fn [p]
                                             (str "*/" p))
                                           (str "*." extension))))
        explode-directory (fn [directory]
                            (take 20
                                  (iterate (fn [p]
                                             (str p "/*"))
                                           directory)))]
    (update base-config
            "exclude" (fn [exclude]
                        (-> (or exclude [])
                            (into (mapcat explode-extension) excluded-file-types)
                            (into (mapcat explode-directory) excluded-directories))))))

(comment
  (clojure.repl/pst)
  (gen-edn-config base-config excluded-file-types excluded-directories)
  (take 10
        (iterate (fn [p]
                   (str "*/" p))
                 "*.png"))
  )

(defn gen-json-config [base-config excluded-file-types excluded-directories]
  (str (json/generate-string
         (gen-edn-config base-config excluded-file-types excluded-directories)
         {:pretty (json/create-pretty-printer
                    (assoc json/default-pretty-print-options
                           :indent-arrays? true))})
       \newline))

(comment
  (println (gen-json-config base-config excluded-file-types excluded-directories))
  )

(defn regen-config [path base-config excluded-file-types excluded-directories]
  (spit path
        (gen-json-config base-config excluded-file-types excluded-directories)))

(def config-path ".do_not_edit-trojansourcedetector.json")

(def excluded-file-types
  (sorted-set
    ))

(def excluded-directories
  (sorted-set
    ".git"
    "target"
    "tmp"
    "bin"))

(def base-config
  (sorted-map
    "detect_unicode" false,
    "detect_bidi" true
    "exclude" []))

(defn -main [& args]
  (assert (empty? args) (pr-str args))
  (regen-config config-path base-config excluded-file-types excluded-directories))

(comment
  (-main))

(when (= *file* (System/getProperty "babashka.file")) (-main))
