(ns ctia.entity.incident.es-store-test
  (:require [ctia.entity.incident.es-store :as sut]
            [clojure.test :refer [deftest is]]))


(deftest append-query-clausse-test
  (is (= {:filter-map {:title "ho no..."}
          :full-text {:query "source:-AMP"}}
         (sut/append-query-clauses {:filter-map {:title "ho no..."}}
                                   ["source:-AMP"])))
  (is (= {:filter-map {:title "ho no..."}
          :full-text {:query "(assignees:guigui) AND (ransomware)"}}
         (sut/append-query-clauses {:filter-map {:title "ho no..."}
                                    :full-text {:query "ransomware"}}
                                   ["assignees:guigui"])))
  (is (= {:filter-map {:title "ho no..."}
          :full-text {:query "(assignees:guigui OR source:-amp) AND (ransomware)"}}
         (sut/append-query-clauses {:filter-map {:title "ho no..."}
                                    :full-text {:query "ransomware"}}
                                   ["assignees:guigui" "source:-amp"]))))

(deftest prepare-impact
  (let [sources "source1,source2"
        fake-config #(get-in {:ctia {:incident {:high-impact {:source sources}}}}
                             %)
        fake-conn-state {:services {:ConfigService {:get-in-config fake-config}}}]
    (is (= {:filter-map {:title "ho no..."}
            :full-text {:query "-source:\"source1\" OR -source:\"source2\""}}
           (sut/prepare-impact fake-conn-state
                               {:filter-map {:title "ho no..."
                                             :high_impact false}})))
    (is (= {:filter-map {:title "ho no..."}
            :full-text {:query "source:\"source1\" OR source:\"source2\""}}
           (sut/prepare-impact fake-conn-state
                               {:filter-map {:title "ho no..."
                                             :high_impact true}})))

    (is (= {:filter-map {:title "ho no..."}
            :full-text {:query "(source:\"source1\" OR source:\"source2\") AND (lucene)"}}
           (sut/prepare-impact fake-conn-state
                               {:filter-map {:title "ho no..."
                                             :high_impact true}
                                :full-text {:query "lucene"}})))

    (is (= {:filter-map {:title "ho no..."}}
           (sut/prepare-impact fake-conn-state
                               {:filter-map {:title "ho no..."}})))))
