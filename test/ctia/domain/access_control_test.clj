(ns ctia.domain.access-control-test
  (:require [ctia.domain.access-control :as sut]
            [clojure.test :refer [deftest testing is]]
            [ctia.properties :as p]
            [ctia.test-helpers.core :as helpers]))

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

(defn test-no-group [tlp sut-fn check-fn]
  (is (check-fn (sut-fn
                 {:tlp tlp
                  :owner "foo"}
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

(defn test-authorized-anonymous [tlp sut-fn check-fn]
  (is (check-fn (sut-fn
                 {:tlp tlp
                  :owner "foo"
                  :groups ["bar"]}
                 {:authorized-anonymous true}))))
;; -- Read tests


;; ---- Max record visibility everyone

(deftest allow-read?-tlp-white-test
  (testing "white TLP should allow document read to everyone"
   (let [get-in-config (helpers/build-get-in-config-fn)
         allow-read? #(sut/allow-read? %1 %2 get-in-config)]
    (test-matching-user "white" allow-read? true?)
    (test-matching-group "white" allow-read? true?)
    (test-user-group-mismatch "white" allow-read? true?)
    (test-no-group "white" allow-read? true?)
    (test-authorized_users-mismatch "white" allow-read? true?)
    (test-authorized_groups-mismatch "white" allow-read? true?)
    (test-authorized_users-match "white" allow-read? true?)
    (test-authorized_groups-match "white" allow-read? true?)
    (test-authorized-anonymous "white" allow-read? true?))))

(deftest allow-read?-tlp-green-test
  (testing "green TLPs should allow document read to everyone"
   (let [get-in-config (helpers/build-get-in-config-fn)
         allow-read? #(sut/allow-read? %1 %2 get-in-config)]
    (test-matching-user "green" allow-read? true?)
    (test-matching-group "green" allow-read? true?)
    (test-no-group "green" allow-read? true?)
    (test-user-group-mismatch "green" allow-read? true?)
    (test-authorized_users-mismatch "green" allow-read? true?)
    (test-authorized_groups-mismatch "green" allow-read? true?)
    (test-authorized_users-match "green" allow-read? true?)
    (test-authorized_groups-match "green" allow-read? true?)
    (test-authorized-anonymous "green" allow-read? true?))))

(deftest allow-read?-tlp-amber-test
  (testing "amber TLPs should allow document read to same group"
   (let [get-in-config (helpers/build-get-in-config-fn)
         allow-read? #(sut/allow-read? %1 %2 get-in-config)]
    (test-matching-user "amber" allow-read? true?)
    (test-matching-group "amber" allow-read? true?)
    (test-no-group "amber" allow-read? false?)
    (test-user-group-mismatch "amber" allow-read? false?)
    (test-authorized_users-mismatch "amber" allow-read? false?)
    (test-authorized_groups-mismatch "amber" allow-read? false?)
    (test-authorized_users-match "amber" allow-read? true?)
    (test-authorized_groups-match "amber" allow-read? true?)
    (test-authorized-anonymous "amber" allow-read? true?))))

(deftest allow-read?-tlp-red-test
  (testing "red TLPs should allow document read to owner only"
   (let [get-in-config (helpers/build-get-in-config-fn)
         allow-read? #(sut/allow-read? %1 %2 get-in-config)]
    (test-matching-user "red" allow-read? true?)
    (test-matching-group "red" allow-read? false?)
    (test-no-group "red" allow-read? false?)
    (test-user-group-mismatch "red" allow-read? false?)
    (test-authorized_users-mismatch "red" allow-read? false?)
    (test-authorized_groups-mismatch "red" allow-read? false?)
    (test-authorized_users-match "red" allow-read? true?)
    (test-authorized_groups-match "red" allow-read? true?)
    (test-authorized-anonymous "red" allow-read? true?))))


;; ---- Max record visibility group

(defn with-max-record-visibility-group [f]
  (swap! (p/global-properties-atom)
         assoc-in [:ctia :access-control :max-record-visibility] "group")
  (f helpers/build-get-in-config-fn)
  (swap! (p/global-properties-atom)
         assoc-in [:ctia :access-control :max-record-visibility] "everyone"))


(deftest allow-read?-tlp-white-max-record-visibility-group-test
  (with-max-record-visibility-group
   (fn [get-in-config]
    (let [allow-read? #(sut/allow-read? %1 %2 get-in-config)]
     (testing "white TLP should disallow document read to everyone"
       (test-matching-user "white" allow-read? true?)
       (test-matching-group "white" allow-read? true?)
       (test-user-group-mismatch "white" allow-read? false?)
       (test-no-group "white" allow-read? false?)
       (test-authorized_users-mismatch "white" allow-read? false?)
       (test-authorized_groups-mismatch "white" allow-read? false?)
       (test-authorized_users-match "white" allow-read? true?)
       (test-authorized_groups-match "white" allow-read? true?))))))

(deftest allow-read?-tlp-green-max-record-visibility-group-test
  (with-max-record-visibility-group
   (fn [get-in-config]
    (let [allow-read? #(sut/allow-read? %1 %2 get-in-config)]
     (testing "green TLPs should disallow document read to everyone"
       (test-matching-user "green" allow-read? true?)
       (test-matching-group "green" allow-read? true?)
       (test-no-group "green" allow-read? false?)
       (test-user-group-mismatch "green" allow-read? false?)
       (test-authorized_users-mismatch "green" allow-read? false?)
       (test-authorized_groups-mismatch "green" allow-read? false?)
       (test-authorized_users-match "green" allow-read? true?)
       (test-authorized_groups-match "green" allow-read? true?))))))

(deftest allow-read?-tlp-amber-max-record-visibility-group-test
  (with-max-record-visibility-group
   (fn [get-in-config]
    (let [allow-read? #(sut/allow-read? %1 %2 get-in-config)]
     (testing "amber TLPs should allow document read to same group"
       (test-matching-user "amber" allow-read? true?)
       (test-matching-group "amber" allow-read? true?)
       (test-no-group "amber" allow-read? false?)
       (test-user-group-mismatch "amber" allow-read? false?)
       (test-authorized_users-mismatch "amber" allow-read? false?)
       (test-authorized_groups-mismatch "amber" allow-read? false?)
       (test-authorized_users-match "amber" allow-read? true?)
       (test-authorized_groups-match "amber" allow-read? true?))))))

(deftest allow-read?-tlp-red-max-record-visibility-group-test
  (with-max-record-visibility-group
   (fn [get-in-config]
    (let [allow-read? #(sut/allow-read? %1 %2 get-in-config)]
     (testing "red TLPs should allow document read to owner only"
       (test-matching-user "red" allow-read? true?)
       (test-matching-group "red" allow-read? false?)
       (test-no-group "red" allow-read? false?)
       (test-user-group-mismatch "red" allow-read? false?)
       (test-authorized_users-mismatch "red" allow-read? false?)
       (test-authorized_groups-mismatch "red" allow-read? false?)
       (test-authorized_users-match "red" allow-read? true?)
       (test-authorized_groups-match "red" allow-read? true?))))))


;; -- Write tests

(deftest allow-write?-tlp-white-test
  (testing "white TLP should allow document write to user/group"
    (test-matching-user "white" sut/allow-write? true?)
    (test-matching-group "white" sut/allow-write? true?)
    (test-no-group "white" sut/allow-write? false?)
    (test-user-group-mismatch "white" sut/allow-write? false?)
    (test-authorized_users-mismatch "white" sut/allow-write? false?)
    (test-authorized_groups-mismatch "white" sut/allow-write? false?)
    (test-authorized_users-match "white" sut/allow-write? true?)
    (test-authorized_groups-match "white" sut/allow-write? true?)
    (test-authorized-anonymous "white" sut/allow-write? false?)))

(deftest allow-write?-tlp-green-test
  (testing "green TLPs should allow document write to user/group"
    (test-matching-user "green" sut/allow-write? true?)
    (test-matching-group "green" sut/allow-write? true?)
    (test-no-group "green" sut/allow-write? false?)
    (test-user-group-mismatch "green" sut/allow-write? false?)
    (test-authorized_users-mismatch "green" sut/allow-write? false?)
    (test-authorized_groups-mismatch "green" sut/allow-write? false?)
    (test-authorized_users-match "green" sut/allow-write? true?)
    (test-authorized_groups-match "green" sut/allow-write? true?)
    (test-authorized-anonymous "green" sut/allow-write? false?)))

(deftest allow-write?-tlp-amber-test
  (testing "amber TLPs should allow document write to same group"
    (test-matching-user "amber" sut/allow-write? true?)
    (test-matching-group "amber" sut/allow-write? true?)
    (test-no-group "amber" sut/allow-write? false?)
    (test-user-group-mismatch "amber" sut/allow-write? false?)
    (test-authorized_users-mismatch "amber" sut/allow-write? false?)
    (test-authorized_groups-mismatch "amber" sut/allow-write? false?)
    (test-authorized_users-match "amber" sut/allow-write? true?)
    (test-authorized_groups-match "amber" sut/allow-write? true?)
    (test-authorized-anonymous "amber" sut/allow-write? false?)))

(deftest allow-write?-tlp-red-test
  (testing "red TLPs should allow document write to owner only"
    (test-matching-user "red" sut/allow-write? true?)
    (test-matching-group "red" sut/allow-write? false?)
    (test-no-group "red" sut/allow-write? false?)
    (test-user-group-mismatch "red" sut/allow-write? false?)
    (test-authorized_users-mismatch "red" sut/allow-write? false?)
    (test-authorized_groups-mismatch "red" sut/allow-write? false?)
    (test-authorized_users-match "red" sut/allow-write? true?)
    (test-authorized_groups-match "red" sut/allow-write? true?)
    (test-authorized-anonymous "red" sut/allow-write? false?)))
