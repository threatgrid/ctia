(ns ctia.domain.access-control-test
  (:require [ctia.domain.access-control :as sut]
            [clojure.test :refer [deftest testing is]]))

(defn test-matching-user [tlp sut-fn check-fn]
  (is (check-fn (sut-fn
                 {:tlp tlp
                  :owner "foo"
                  :groups ["bar"]}
                 {:login "foo"
                  :groups ["foo" "bar"]}))))

(defn test-matching-group [tlp sut-fn check-fn]
  (is (check-fn (sut-fn
                 {:tlp tlp
                  :owner "bar"
                  :groups ["foobar"]}
                 {:login "foo"
                  :groups ["foobar"]}))))

(defn test-user-group-mismatch [tlp sut-fn check-fn]
  (is (check-fn (sut-fn
                 {:tlp tlp
                  :owner "foo"
                  :groups ["bar"]}
                 {:login "anyone"
                  :groups ["everyone"]}))))

(defn test-authorized_users-mismatch [tlp sut-fn check-fn]
  (is (check-fn (sut-fn
                 {:tlp tlp
                  :owner "foo"
                  :groups ["bar"]
                  :authorized_users ["someone"]}
                 {:login "anyone"
                  :groups ["everyone"]}))))

(defn test-authorized_groups-mismatch [tlp sut-fn check-fn]
  (is (check-fn (sut-fn
                 {:tlp tlp
                  :owner "foo"
                  :groups ["bar"]
                  :authorized_grouos ["somegroup"]}
                 {:login "anyone"
                  :groups ["everyone"]}))))

(defn test-authorized_users-match [tlp sut-fn check-fn]
  (is (check-fn (sut-fn
                 {:tlp tlp
                  :owner "foo"
                  :groups ["bar"]
                  :authorized_users ["anyone"]}
                 {:login "anyone"
                  :groups ["everyone"]}))))

(defn test-authorized_groups-match [tlp sut-fn check-fn]
  (is (check-fn (sut-fn
                 {:tlp tlp
                  :owner "foo"
                  :groups ["bar"]
                  :authorized_groups ["everyone"]}
                 {:login "anyone"
                  :groups ["everyone"]}))))

;; -- Read tests

(deftest allow-read?-tlp-white-test
  (testing "white TLP should allow document read to everyone"
    (test-matching-user "white" sut/allow-read? true?)
    (test-matching-group "white" sut/allow-read? true?)
    (test-user-group-mismatch "white" sut/allow-read? true?)
    (test-authorized_users-mismatch "white" sut/allow-read? true?)
    (test-authorized_groups-mismatch "white" sut/allow-read? true?)
    (test-authorized_users-match "white" sut/allow-read? true?)
    (test-authorized_groups-match "white" sut/allow-read? true?)))

(deftest allow-read?-tlp-green-test
  (testing "green TLPs should allow document read to everyone"
    (test-matching-user "green" sut/allow-read? true?)
    (test-matching-group "green" sut/allow-read? true?)
    (test-user-group-mismatch "green" sut/allow-read? true?)
    (test-authorized_users-mismatch "green" sut/allow-read? true?)
    (test-authorized_groups-mismatch "green" sut/allow-read? true?)
    (test-authorized_users-match "green" sut/allow-read? true?)
    (test-authorized_groups-match "green" sut/allow-read? true?)))

(deftest allow-read?-tlp-amber-test
  (testing "amber TLPs should allow document read to same group"
    (test-matching-user "amber" sut/allow-read? true?)
    (test-matching-group "amber" sut/allow-read? true?)
    (test-user-group-mismatch "amber" sut/allow-read? false?)
    (test-authorized_users-mismatch "amber" sut/allow-read? false?)
    (test-authorized_groups-mismatch "amber" sut/allow-read? false?)
    (test-authorized_users-match "amber" sut/allow-read? true?)
    (test-authorized_groups-match "amber" sut/allow-read? true?)))

(deftest allow-read?-tlp-red-test
  (testing "red TLPs should allow document read to owner only"
    (test-matching-user "red" sut/allow-read? true?)
    (test-matching-group "red" sut/allow-read? false?)
    (test-user-group-mismatch "red" sut/allow-read? false?)
    (test-authorized_users-mismatch "red" sut/allow-read? false?)
    (test-authorized_groups-mismatch "red" sut/allow-read? false?)
    (test-authorized_users-match "red" sut/allow-read? true?)
    (test-authorized_groups-match "red" sut/allow-read? true?)))

;; -- Write tests

(deftest allow-write?-tlp-white-test
  (testing "white TLP should allow document write to user/group"
    (test-matching-user "white" sut/allow-write? true?)
    (test-matching-group "white" sut/allow-write? true?)
    (test-user-group-mismatch "white" sut/allow-write? false?)
    (test-authorized_users-mismatch "white" sut/allow-write? false?)
    (test-authorized_groups-mismatch "white" sut/allow-write? false?)
    (test-authorized_users-match "white" sut/allow-write? true?)
    (test-authorized_groups-match "white" sut/allow-write? true?)))

(deftest allow-write?-tlp-green-test
  (testing "green TLPs should allow document write to user/group"
    (test-matching-user "green" sut/allow-write? true?)
    (test-matching-group "green" sut/allow-write? true?)
    (test-user-group-mismatch "green" sut/allow-write? false?)
    (test-authorized_users-mismatch "green" sut/allow-write? false?)
    (test-authorized_groups-mismatch "green" sut/allow-write? false?)
    (test-authorized_users-match "green" sut/allow-write? true?)
    (test-authorized_groups-match "green" sut/allow-write? true?)))

(deftest allow-write?-tlp-amber-test
  (testing "amber TLPs should allow document write to same group"
    (test-matching-user "amber" sut/allow-write? true?)
    (test-matching-group "amber" sut/allow-write? true?)
    (test-user-group-mismatch "amber" sut/allow-write? false?)
    (test-authorized_users-mismatch "amber" sut/allow-write? false?)
    (test-authorized_groups-mismatch "amber" sut/allow-write? false?)
    (test-authorized_users-match "amber" sut/allow-write? true?)
    (test-authorized_groups-match "amber" sut/allow-write? true?)))

(deftest allow-write?-tlp-red-test
  (testing "red TLPs should allow document write to owner only"
    (test-matching-user "red" sut/allow-write? true?)
    (test-matching-group "red" sut/allow-write? false?)
    (test-user-group-mismatch "red" sut/allow-write? false?)
    (test-authorized_users-mismatch "red" sut/allow-write? false?)
    (test-authorized_groups-mismatch "red" sut/allow-write? false?)
    (test-authorized_users-match "red" sut/allow-write? true?)
    (test-authorized_groups-match "red" sut/allow-write? true?)))
