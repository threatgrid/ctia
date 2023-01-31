(ns ctia.stores.es.search
  (:require [ctia.schemas.core :refer [SearchExtension]]
            [clojure.string :as str]
            [schema.core :as s]))

(defn parse-search-params-op
  [{:keys [op field-name sort_order] :as params} :- ConcreteSortExtension
   default-sort_order :- (s/cond-pre s/Str s/Keyword)]
  (assert nil "FIXME"))
