;; clj -T:build uber
(ns build
  (:require project-config
            [clojure.tools.build.api :as b]))

(def lib project-config/ctia-jar-coords)
(def version (format "1.15.1.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def uber-file
  (format "target/%s.jar" (name lib))
  #_(format "target/%s-%s-standalone.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber [_]
  (clean nil)
  ;; TODO deal with classpath conflicts
  (b/copy-dir {:src-dirs (concat project-config/source-paths
                                 project-config/resource-paths)
               :target-dir class-dir})
  (b/compile-clj {:basis basis
                  :ns-compile [project-config/main-ns]
                  :src-dirs project-config/source-paths
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis basis
           :main project-config/main-ns}))
