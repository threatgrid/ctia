(ns ctia.import.threatgrid.ttps
  (:require [clojure.data.json :as json]
            [cheshire.core :as cjson]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [ctim.schemas.ttp :as ttp]
            [schema.core :as s]
            [ctia.import.threatgrid.http :as http]
            [ctia.lib.time :as time]))

(s/defschema TGTTP
  {(s/required-key "id") s/Str
   (s/required-key "title") s/Str
   (s/required-key "description") s/Str
   (s/required-key "timestamp") s/Str
   (s/required-key "type") s/Str
   (s/required-key "expires") s/Str
   (s/required-key "indicators") [(s/maybe s/Str)]})

(s/defn tg-ttp->ctim-ttp :- ttp/NewTTP
  [{:strs [id title description timestamp type expires indicators]
    :as tg-ttp} :- TGTTP]
  {:title id
   :description description
   :short_description description
   :tlp "green"
   :valid_time (time/date-str->valid-time timestamp 30)
   :indicators indicators
   :ttp_type type
   :source "Threat Grid"})

(defn load-ttps-from-file
  [tg-ttps & {:keys [ctia-uri api-key]}]
  (doall (http/post-to-ctia (map tg-ttp->ctim-ttp tg-ttps)
                            :ttps
                            ctia-uri)))

(defn -main [& [file-path ctia-uri api-key :as _args_]]
  (println "Running TTP Importer.")
  (assert (not-empty file-path) "File path must be set")
  (assert (not-empty ctia-uri) "URI must be set")
  (-> (slurp file-path)
      cjson/parse-string
      (load-ttps-from-file :ctia-uri ctia-uri
                           :api-key (not-empty api-key))
      pprint))
