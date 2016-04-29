(ns ctia.lib.pagination)

(def default-limit 100)

(defn response
  "Make a paginated response adding summary info as metas"
  [results offset limit hits]
  (let [offset (or offset 0)
        limit (or limit default-limit)
        previous-offset (- offset limit)
        next-offset (+ offset limit)
        previous? (pos? offset)
        next? (> hits next-offset)
        previous {:previous {:limit limit
                             :offset (if (> previous-offset 0)
                                       previous-offset 0)}}
        next {:next {:limit limit
                     :offset next-offset}}]
    (with-meta results
      (merge
       {:total-hits hits}
       (when previous? previous)
       (when next? next)))))
