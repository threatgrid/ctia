(ns ctia.test-helpers.http
  (:require
   [clojure.string :as string]
   [clj-momo.test-helpers.http-assert-1 :as mthh]
   [ctia.test-helpers.core :as th]))

(def api-key "45c1f5e3f05d0")

(defn doc-id->rel-url
  "given a doc id (url) make a relative url for test queries"
  [doc-id]
  (when doc-id
    (string/replace doc-id #".*(?=ctia)" "")))

(defn assert-post [app & args]
  (apply (mthh/with-port-fn-and-api-key
           (partial th/get-http-port app)
           api-key
           mthh/assert-post)
         args))
