(ns ctia.entity.incident.es-store-test
  (:require [ctia.entity.incident.es-store :as sut]
            [clojure.test :refer [deftest is]]))


(deftest append-query-clause-test
  (is (= {:filter-map {:title "ho no..."}
          :full-text {:query "source:-AMP"}}
         (sut/append-query-clause {:filter-map {:title "ho no..."}}
                                  "source:-AMP")))
  (is (= {:filter-map {:title "ho no..."}
          :full-text {:query "ransomware assignees:guigui"}}
         (sut/append-query-clause {:filter-map {:title "ho no..."}
                                   :full-text {:query "ransomware"}}
                                  "assignees:guigui"))))

(deftest prepare-impact
  (let [fake-config #(get-in {:ctia {:incident {:high-impact {:source "fake source"}}}}
                             %)
        fake-conn-state {:services {:ConfigService {:get-in-config fake-config}}}]
    (is (= {:filter-map {:title "ho no..."}
            :full-text {:query "-source:\"fake source\""}}
           (sut/prepare-impact fake-conn-state
                               {:filter-map {:title "ho no..."
                                             :high_impact false}})))
    (is (= {:filter-map {:title "ho no..."
                         :source "fake source"}}
           (sut/prepare-impact fake-conn-state
                               {:filter-map {:title "ho no..."
                                             :high_impact true}})))

    (is (= {:filter-map {:title "ho no..."
                         :source "fake source"}
            :full-text {:query "lucene"}}
           (sut/prepare-impact fake-conn-state
                               {:filter-map {:title "ho no..."
                                             :high_impact true}
                                :full-text {:query "lucene"}})))

    (is (= {:filter-map {:title "ho no..."}}
           (sut/prepare-impact fake-conn-state
                               {:filter-map {:title "ho no..."}})))))
