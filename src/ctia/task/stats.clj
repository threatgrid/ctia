(ns ctia.task.stats)

(defn topn-orgs
  [n]
  {:agg-key :top-orgs
   :agg-type :topn
   :aggregate-on :groups
   :limit n})

(defn topn-sources
  [n]
  {:agg-key :top-sources
   :agg-type :topn
   :aggregate-on :groups
   :limit n})

;; TODO: remove unused var
#_(defn topn-source-top-orgs
  [n-sources n-orgs]
  (assoc (topn-sources n-sources)
         :aggs
         (topn-orgs n-orgs)))
