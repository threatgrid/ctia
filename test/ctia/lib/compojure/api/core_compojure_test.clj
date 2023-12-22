(ns ctia.lib.compojure.api.core-compojure-test
  (:require [ctia.lib.compojure.api.core-compojure :as sut]
            [clojure.test :refer [deftest is]]
            [ring.swagger.json-schema :refer [describe]]
            [schema.core :as s]))

(defmacro with-deterministic-gensym [& body]
  `(with-bindings {#'sut/*gensym* (let [a# (atom -1)]
                                    (fn [s#]
                                      {:pre [(string? s#)]
                                       :post [(symbol? ~'%)]}
                                      (symbol (str s# "__" (swap! a# inc')))))}
     (do ~@body)))

(defn dexpand-1 [form]
  (with-deterministic-gensym
    (macroexpand-1 form)))

(deftest context-expansion-test
  ;; :tags is unevaluated
  (is (= '(clojure.core/let [routes__0 (compojure.api.core/routes routes)]
            (compojure.api.core/context
              "/my-route" []
              :tags #{:bar :foo}
              routes__0))
         (dexpand-1
           `(sut/context
              "/my-route" []
              :tags #{:foo :bar}
              ~'routes))))
  ;; :capabilities is evaluated
  (is (= '(clojure.core/let [routes__0 (compojure.api.core/routes routes)
                             capabilities__1 #{:bar :foo}]
            (compojure.api.core/context
              "/my-route" []
              :capabilities capabilities__1
              routes__0))
         (dexpand-1
           `(sut/context
              "/my-route" []
              :capabilities #{:foo :bar}
              ~'routes))))
  ;; :description is evaluated
  (is (= '(clojure.core/let [routes__0 (compojure.api.core/routes routes)
                             description__1 (clojure.core/str "Foo" "bar")]
            (compojure.api.core/context
              "/my-route" []
              :description description__1
              routes__0))
         (dexpand-1
           `(sut/context
              "/my-route" []
              :description (str "Foo" "bar")
              ~'routes))))
  ;; :responses is evaluated
  (is (= '(clojure.core/let [routes__0 (compojure.api.core/routes routes)
                             responses__1 {200 {:schema {:my-schema #{}}}}]
            (compojure.api.core/context
              "/my-route" []
              :responses responses__1
              routes__0))
         (dexpand-1
           `(sut/context
              "/my-route" []
              :responses {200 {:schema {:my-schema #{}}}}
              ~'routes))))
  ;; :summary is evaluated
  (is (= '(clojure.core/let [routes__0 (compojure.api.core/routes routes)
                             summary__1 (clojure.core/str "a" "summary")]
            (compojure.api.core/context
              "/my-route" []
              :summary summary__1
              routes__0))
         (dexpand-1
           `(sut/context
              "/my-route" []
              :summary (str "a" "summary")
              ~'routes)))))

;; adapted from clojure.repl/root-cause, but unwraps compiler exceptions
(defn root-cause [t]
  (loop [cause t]
    (if-let [cause (.getCause cause)]
      (recur cause)
      cause)))

(defn is-banned-macro [form msg]
  (try (dexpand-1 form)
       (is false (pr-str form))
       (catch Exception e
         (is (= msg (ex-message (root-cause e))) (pr-str form)))))

(deftest context-banned-test
  (is-banned-macro
    `(sut/context
       "/my-route" []
       :path-params [~'id :- s/Str]
       ~'routes)
    "Not allowed these options in `context`, push into HTTP verbs instead: (:path-params)")
  (is-banned-macro
    `(sut/context
       "/my-route" []
       :query-params [{~'wait_for :- (describe s/Bool "wait for patched entity to be available for search") nil}]
       ~'routes)
    "Not allowed these options in `context`, push into HTTP verbs instead: (:query-params)")
  (is-banned-macro
    `(sut/context
       "/my-route" []
       :auth-identity ~'identity
       ~'routes)
    "Not allowed these options in `context`, push into HTTP verbs instead: (:auth-identity)")
  (is-banned-macro
    `(sut/context
       "/my-route" []
       :identity-map ~'identity-map
       ~'routes)
    "Not allowed these options in `context`, push into HTTP verbs instead: (:identity-map)")
  (is-banned-macro
    `(sut/context
       "/my-route" []
       :return s/Str
       ~'routes)
    (str ":return is banned, please use :responses instead.\n"
         "In this case, :return schema.core/Str is equivalent to :responses {200 {:schema schema.core/Str}}.\n"
         "For 204, you can use :responses {204 nil}.\nFor catch-all, use :responses {:default {:schema SCHEMA}}")))

(deftest endpoints-banned-test
  (doseq [macro [`sut/GET
                 `sut/ANY
                 `sut/HEAD
                 `sut/PATCH
                 `sut/DELETE
                 `sut/OPTIONS
                 `sut/POST
                 `sut/PUT]]
    (is-banned-macro
      `(~macro
         "/my-route" []
         :return s/Str
         {:status 200})
      (str ":return is banned, please use :responses instead.\n"
           "In this case, :return schema.core/Str is equivalent to :responses {200 {:schema schema.core/Str}}.\n"
           "For 204, you can use :responses {204 nil}.\nFor catch-all, use :responses {:default {:schema SCHEMA}}"))))
