(ns ctia.lib.compojure.api.core-test
  (:require [ctia.lib.compojure.api.core :as sut]
            [clojure.test :refer [deftest is testing]]
            [compojure.api.api :refer [api]]
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
  ;; :return is evaluated
  (is (= '(clojure.core/let [routes__0 (compojure.api.core/routes routes)
                             return__1 {:my-schema #{}}]
            (compojure.api.core/context
              "/my-route" []
              :return return__1
              routes__0))
         (dexpand-1
           `(sut/context
              "/my-route" []
              :return {:my-schema #{}}
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

(deftest endpoint-expansion-test
  ;; :tags is unevaluated
  (is (= '(compojure.api.core/ANY
            "/my-route" []
            :tags #{:bar :foo}
            {:status 200})
         (dexpand-1
           `(sut/ANY
              "/my-route" []
              :tags #{:foo :bar}
              {:status 200}))))
  ;; :capabilities is evaluated
  (is (= '(clojure.core/let [capabilities__0 #{:bar :foo}]
            (compojure.api.core/ANY
              "/my-route" []
              :capabilities capabilities__0
              {:status 200}))
         (dexpand-1
           `(sut/ANY
              "/my-route" []
              :capabilities #{:foo :bar}
              {:status 200}))))
  ;; :description is evaluated
  (is (= '(clojure.core/let [description__0 (clojure.core/str "Foo" "bar")]
            (compojure.api.core/ANY
              "/my-route" []
              :description description__0
              {:status 200}))
         (dexpand-1
           `(sut/ANY
              "/my-route" []
              :description (str "Foo" "bar")
              {:status 200}))))
  ;; :return is evaluated
  (is (= '(clojure.core/let [return__0 {:my-schema #{}}]
            (compojure.api.core/ANY
              "/my-route" []
              :return return__0
              {:status 200}))
         (dexpand-1
           `(sut/ANY
              "/my-route" []
              :return {:my-schema #{}}
              {:status 200}))))
  ;; :summary is evaluated
  (is (= '(clojure.core/let [summary__0 (clojure.core/str "a" "summary")]
            (compojure.api.core/ANY
              "/my-route" []
              :summary summary__0
              {:status 200}))
         (dexpand-1
           `(sut/ANY
              "/my-route" []
              :summary (str "a" "summary")
              {:status 200})))))

;; adapted from clojure.repl/root-cause, but unwraps compiler exceptions
(defn root-cause [t]
  (loop [cause t]
    (if-let [cause (.getCause cause)]
      (recur cause)
      cause)))

(defn is-banned-expansion [form msg]
  (try (dexpand-1 form)
       (is false (pr-str form))
       (catch Exception e
         (is (= msg (ex-message (root-cause e))) (pr-str form)))))

(deftest context-banned-test
  (is-banned-expansion
    `(sut/context
       "/my-route" []
       :path-params [~'id :- s/Str]
       ~'routes)
    "Not allowed these options in `context`, push into HTTP verbs instead: (:path-params)")
  (is-banned-expansion
    `(sut/context
       "/my-route" []
       :query-params [{~'wait_for :- (describe s/Bool "wait for patched entity to be available for search") nil}]
       ~'routes)
    "Not allowed these options in `context`, push into HTTP verbs instead: (:query-params)")
  (is-banned-expansion
    `(sut/context
       "/my-route" []
       :auth-identity ~'identity
       ~'routes)
    "Not allowed these options in `context`, push into HTTP verbs instead: (:auth-identity)")
  (is-banned-expansion
    `(sut/context
       "/my-route" []
       :identity-map ~'identity-map
       ~'routes)
    "Not allowed these options in `context`, push into HTTP verbs instead: (:identity-map)"))



;; this test shows that we are not allowed to let-bind the schema of :body in a HTTP verb since it would
;; break scoping.
(deftest cannot-bind-req-and-dynamic-restructure-test 
  (is-banned-expansion
    `(sut/ANY "*" ~'req
              :body [~'body ~'(not-a-symbol)]
              {:status 200
               :body g})
    "Please let-bind the :body schema like so: (let [s# (not-a-symbol)] (ANY \"*\" req :body [body s#] ...))")
  ;;TODO fix the :capabilities test and verify if we actually need to let-bind :capabilities
  (is-banned-expansion
    `(sut/ANY "*" ~'req
              :capabilities ~'(not-a-symbol)
              {:status 200
               :body g})
    "Please let-bind :capabilities like so: (let [v# (not-a-symbol)] (ANY \"*\" req :capabilities s# ...))")
  (is-banned-expansion
    `(sut/ANY "*" ~'req
              :return ~'(not-a-symbol)
              {:status 200
               :body g})
    "Please let-bind :return like so: (let [v# (not-a-symbol)] (ANY \"*\" req :return s# ...))"))

(deftest endpoint-initializes-once-test
  ;; :body schema only evaluates at initialization time
  (testing ":body"
    (let [times (atom 0)
          route (sut/POST "*" []
                          :body [body (do (swap! times inc) s/Any) {:description
                                                                    ;; this is never evaluated
                                                                    (do (swap! times inc) "foo")}]
                          {:status 200
                           :body ["yes" body]})
          _ (is (= 1 @times))
          _ (dotimes [_ 10]
              (let [g (str (gensym))]
                (is (= ["yes"
                        ;;FIXME
                        nil #_g]
                       (:body ((:handler route) {:request-method :post :uri "/" :body g}))))))]
      (is (= 1 @times))))
  ;; :description only evaluates at initialization time
  (testing ":description"
    (let [g (str (gensym))
          times (atom 0)
          route (sut/ANY "*" []
                         :description (do (swap! times inc) "thing")
                         {:status 200
                          :body g})
          _ (is (= 1 @times))
          _ (dotimes [_ 10]
              (is (= g (:body ((:handler route) {:uri "/"})))))]
      (is (= 1 @times))))
  ;; :return only evaluates at initialization time
  (testing ":return"
    (let [g (str (gensym))
          times (atom 0)
          route (sut/ANY "*" []
                         :return (do (swap! times inc) s/Any)
                         {:status 200
                          :body g})
          _ (is (= 1 @times))
          _ (dotimes [_ 10]
              (is (= g (:body ((:handler route) {:uri "/"})))))]
      (is (= 1 @times))))
  ;; :summary only evaluates at initialization time
  (testing ":summary"
    (let [g (str (gensym))
          times (atom 0)
          route (sut/ANY "*" []
                         :summary (do (swap! times inc) "foo")
                         {:status 200
                          :body g})
          _ (is (= 1 @times))
          _ (dotimes [_ 10]
              (is (= g (:body ((:handler route) {:uri "/"})))))]
      (is (= 1 @times))))
  ;; :capabilities only evaluates at initialization time
  #_ ;;FIXME needs an authenticated request
  (testing ":capabilities"
    (let [g (str (gensym))
          times (atom 0)
          route (sut/ANY "*" []
                         :capabilities (do (swap! times inc) #{})
                         {:status 200
                          :body g})
          _ (is (= 1 @times))
          _ (dotimes [_ 10]
              (is (= g (:body ((:handler route) {:uri "/"})))))]
      (is (= 1 @times))))
  ;; :path-params schema only evaluates at initialization time
  (testing ":path-params"
    (let [times (atom 0)
          route (sut/ANY "/:id" []
                         :path-params [id :- (do (swap! times inc) s/Str)]
                         {:status 200
                          :body id})
          _ (is (= 1 @times))
          _ (dotimes [_ 10]
              (let [g (str (gensym))]
                (is (= g (:body ((:handler route) {:uri (str "/" g)}))))))]
      (is (= 1 @times))))
  ;; :query-params schema only evaluates at initialization time
  #_ ;;FIXME construct valid query params
  (testing ":query-params"
    (let [times (atom 0)
          g (str (gensym))
          route (sut/ANY "*" []
                         :query-params [{wait_for :- (do (swap! times inc)
                                                         (describe s/Bool "wait for created entities to be available for search")) nil}]
                         {:status 200
                          :body [g wait_for]})
          _ (is (= 1 @times))
          _ (dotimes [_ 10]
              (let [wait_for (rand-nth [true false])]
                (is (= [g wait_for] (:body ((:handler route) {:uri (str "wait_for=" wait_for)}))))))]
      (is (= 1 @times))))
  )
