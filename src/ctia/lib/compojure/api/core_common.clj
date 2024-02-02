(ns ctia.lib.compojure.api.core-common)

(defn check-return-banned! [options]
  (when-some [[_ schema] (find options :return)]
    (throw (ex-info (format (str ":return is banned, please use :responses instead.\n"
                                 "In this case, :return %s is equivalent to :responses {200 {:schema %s}}.\n"
                                 "For 204, you can use :responses {204 nil}.\n"
                                 "For catch-all, use :responses {:default {:schema SCHEMA}}")
                            schema schema)
                    {}))))
