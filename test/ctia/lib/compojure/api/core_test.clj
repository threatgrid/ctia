(ns ctia.lib.compojure.api.core-test
  (:require [ctia.lib.compojure.api.core :as sut]
            [clojure.test :refer [deftest is testing]]
            [compojure.api.api :refer [api]]
            [ring.swagger.json-schema :refer [describe]]
            [ctia.auth.allow-all :as allow-all]
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

(defn is-expand [form arrow expansion]
  (assert (= :=> arrow))
  (is (= expansion (dexpand-1 form))))

(deftest context-expansion-test
  ;; :tags is unevaluated
  (is-expand `(sut/context
                "/my-route" []
                :tags #{:foo :bar}
                ~'routes)
             :=> '(clojure.core/let [routes__0 (compojure.api.core/routes routes)]
                    (compojure.api.core/context
                      "/my-route" []
                      :tags #{:bar :foo}
                      routes__0)))
  ;; :capabilities is evaluated
  (is-expand `(sut/context
                "/my-route" []
                :capabilities #{:foo :bar}
                ~'routes)
             :=> '(clojure.core/let [routes__0 (compojure.api.core/routes routes)
                                     capabilities__1 #{:bar :foo}]
                    (compojure.api.core/context
                      "/my-route" []
                      :capabilities capabilities__1
                      routes__0)))
  ;; :description is evaluated
  (is-expand `(sut/context
                "/my-route" []
                :description (str "Foo" "bar")
                ~'routes)
             :=> '(clojure.core/let [routes__0 (compojure.api.core/routes routes)
                                     description__1 (clojure.core/str "Foo" "bar")]
                    (compojure.api.core/context
                      "/my-route" []
                      :description description__1
                      routes__0)))
  ;; :return is evaluated
  (is-expand `(sut/context
                "/my-route" []
                :return {:my-schema #{}}
                ~'routes)
             :=> '(clojure.core/let [routes__0 (compojure.api.core/routes routes)
                                     return__1 {:my-schema #{}}]
                    (compojure.api.core/context
                      "/my-route" []
                      :return return__1
                      routes__0)))
  ;; :summary is evaluated
  (is-expand `(sut/context
                "/my-route" []
                :summary (str "a" "summary")
                ~'routes)
             :=> '(clojure.core/let [routes__0 (compojure.api.core/routes routes)
                                     summary__1 (clojure.core/str "a" "summary")]
                    (compojure.api.core/context
                      "/my-route" []
                      :summary summary__1
                      routes__0))))

(deftest endpoint-expansion-test
  ;; :tags is unevaluated
  (is-expand `(sut/ANY
                "/my-route" []
                :tags #{:foo :bar}
                {:status 200})
             :=> '(compojure.api.core/ANY
                    "/my-route" []
                    :tags #{:bar :foo}
                    {:status 200}))
  ;; :capabilities is evaluated
  (is-expand `(sut/ANY
                "/my-route" []
                :capabilities #{:foo :bar}
                {:status 200})
             :=> '(clojure.core/let [capabilities__0 #{:bar :foo}]
                    (compojure.api.core/ANY
                      "/my-route" []
                      :capabilities capabilities__0
                      {:status 200})))
  ;; :description is preserved, since it's just for :swagger
  (is-expand `(sut/ANY
                "/my-route" []
                :description (str "Foo" "bar")
                {:status 200})
             :=> '(compojure.api.core/ANY
                    "/my-route" []
                    :description (clojure.core/str "Foo" "bar")
                    {:status 200}))
  ;; :return is evaluated
  (is-expand `(sut/ANY
                "/my-route" []
                :return {:my-schema #{}}
                {:status 200})
             :=> '(clojure.core/let [return__0 {:my-schema #{}}]
                    (compojure.api.core/ANY
                      "/my-route" []
                      :return return__0
                      {:status 200})))
  ;; :summary is preserved, since it's just for :swagger
  (is-expand `(sut/ANY
                "/my-route" []
                :summary (str "a" "summary")
                {:status 200})
             :=> '(compojure.api.core/ANY
                    "/my-route" []
                    :summary (clojure.core/str "a" "summary")
                    {:status 200}))
  ;; :body let-binds its schema
  (is-expand `(sut/ANY
                "/my-route" []
                :body [~'body {:a s/Str}]
                {:status 200})
             :=> '(clojure.core/let [body__0 {:a schema.core/Str}]
                    (compojure.api.core/ANY
                      "/my-route" []
                      :body [body body__0]
                      {:status 200})))
  ;; :path-params let-binds its schemas and defaults
  (is-expand `(sut/ANY
                "/:left/:right" []
                :path-params [~'left :- s/Int
                              {~'right :- s/Str :default}]
                {:status 200})
             :=> '(clojure.core/let [left__1 schema.core/Int
                                     right__2 schema.core/Str
                                     right-default__3 :default]
                    (compojure.api.core/ANY
                      "/:left/:right" []
                      :path-params [left :- left__1
                                    {right :- right__2 right-default__3}]
                      {:status 200})))
  ;; :query-params let-binds its schemas and defaults
  (is-expand `(sut/ANY
                "*" []
                :query-params [~'left :- s/Int
                              {~'right :- s/Str :default}]
                {:status 200})
             :=> '(clojure.core/let [left__1 schema.core/Int
                                     right__2 schema.core/Str
                                     right-default__3 :default]
                    (compojure.api.core/ANY
                      "*" []
                      :query-params [left :- left__1
                                     {right :- right__2 right-default__3}]
                      {:status 200}))))

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
         (is (= msg (ex-message (root-cause e)))
             (pr-str form)))))

(deftest context-banned-test
  (is-banned-expansion
    `(sut/context
       "/my-route" []
       :body [~'id :- s/Str]
       ~'routes)
    "Not allowed these options in `context`, push into HTTP verbs instead: (:body)")
  (is-banned-expansion
    `(sut/context
       "/my-route" []
       :query [~'query {}]
       ~'routes)
    "Not allowed these options in `context`, push into HTTP verbs instead: (:query)")
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
    "Please let-bind :return like so: (let [v# (not-a-symbol)] (ANY \"*\" req :return s# ...))")
  (is-banned-expansion
    `(sut/ANY "/:id" ~'req
              :path-params [~'id :- ~'(not-a-symbol)]
              {:status 200
               :body g})
    "Please let-bind id in :path-params like so: (let [s# (not-a-symbol)] (ANY \"/:id\" req :path-params [id :- s#] ...))")
  (is-banned-expansion
    `(sut/ANY "/:id" ~'req
              :path-params [{~'id :- ~'(dynamic-schema) ~'(dynamic-default)}]
              {:status 200
               :body g})
    "Please let-bind id in :path-params like so: (let [s# (dynamic-schema) d# (dynamic-default)] (ANY \"/:id\" req :path-params {id :-, s# d#} ...))")
  (is-banned-expansion
    `(sut/ANY "*" ~'req
              :query-params [~'id :- ~'(not-a-symbol)]
              {:status 200
               :body g})
    "Please let-bind id in :query-params like so: (let [s# (not-a-symbol)] (ANY \"*\" req :query-params [id :- s#] ...))")
  (is-banned-expansion
    `(sut/ANY "*" ~'req
              :query-params [{~'id :- ~'(dynamic-schema) ~'(dynamic-default)}]
              {:status 200
               :body g})
    "Please let-bind id in :query-params like so: (let [s# (dynamic-schema) d# (dynamic-default)] (ANY \"*\" req :query-params {id :-, s# d#} ...))"))

(deftest endpoint-initializes-once-test
  ;; :body schema only evaluates at initialization time
  (testing ":body"
    (let [times (atom 0)
          route (sut/POST "*" []
                          :body [body (do (swap! times inc) s/Any) {:description
                                                                    ;; this is never evaluated
                                                                    (do (swap! times + 10) "foo")}]
                          {:status 200
                           :body ["yes" body]})
          _ (is (= 1 @times))
          _ (dotimes [_ 10]
              (let [g (str (gensym))]
                (is (= ["yes" g]
                       (:body ((:handler route) {:request-method :post :uri "/" :body-params g}))))))]
      (is (= 1 @times))))
  ;; :query schema only evaluates at initialization time
  (testing ":query"
    (let [times (atom 0)
          g (str (gensym))
          route (sut/ANY "*" []
                         :query [query (do (swap! times inc) {:a s/Any})]
                         {:status 200
                          :body [g query]})
          _ (is (= 1 @times))
          _ (dotimes [_ 10]
              (is (= [g {:a "b"}] (:body ((:handler route) {:uri "/" :query-params {"a" "b"}})))))]
      (is (= 1 @times))))
  ;; :path-params schema only evaluates at initialization time
  (testing ":path-params"
    (let [times (atom {:left 0 :right 0 :right-default 0})
          g (str (gensym))
          route (sut/ANY "/:left/:right" []
                         :path-params [left :- (do (swap! times update :left inc) s/Any)
                                       {right :-
                                        (do (swap! times update :right inc) s/Any)
                                        (do (swap! times update :right-default inc) :default)}]
                         {:status 200
                          :body [g left right]})
          _ (is (= {:left 1 :right 1 :right-default 1} @times))
          _ (dotimes [_ 10]
              (is (= [g "left" "right"] (:body ((:handler route) {:uri "/left/right"})))))]
      (is (= {:left 1 :right 1 :right-default 1} @times))))
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
  ;; :no-doc only evaluates at initialization time
  (testing ":no-doc"
    (let [g (str (gensym))
          times (atom 0)
          route (sut/ANY "*" []
                         :no-doc (do (swap! times inc) true)
                         {:status 200
                          :body g})
          _ (is (= 1 @times))
          _ (dotimes [_ 10]
              (is (= g (:body ((:handler route) {:uri "/"})))))]
      (is (= 1 @times))))
  ;; :produces only evaluates at initialization time
  (testing ":produces"
    (let [g (str (gensym))
          times (atom 0)
          route (sut/ANY "*" []
                         :produces (do (swap! times inc) #{})
                         {:status 200
                          :body g})
          _ (is (= 1 @times))
          _ (dotimes [_ 10]
              (is (= g (:body ((:handler route) {:uri "/"})))))]
      (is (= 1 @times))))
  ;; :capabilities only evaluates at initialization time
  (testing ":capabilities"
    (let [g (str (gensym))
          times (atom 0)
          route (sut/ANY "*" []
                         :capabilities (do (swap! times inc) #{})
                         {:status 200
                          :body g})
          _ (is (= 1 @times))
          _ (dotimes [_ 10]
              (is (= g (:body ((:handler route) {:uri "/"
                                                 :identity allow-all/identity-singleton})))))]
      (is (= 1 @times))))
  ;; :path-params schema only evaluates at initialization time
  (testing ":path-params"
    (let [times (atom {:left 0 :right 0 :right-default 0})
          route (sut/ANY "/:left/:right" []
                         :path-params [left :- (do (swap! times update :left inc) s/Str)
                                       {right :- (do (swap! times update :right inc) s/Str)
                                        (do (swap! times update :right-default inc) (str "default"))}]
                         {:status 200
                          :body [left right]})
          _ (is (= {:left 1 :right 1 :right-default 1} @times))
          _ (dotimes [_ 10]
              (let [g (str (gensym))]
                (is (= ["left" (str "right" g)] (:body ((:handler route) {:uri (str "/left/right" g)}))))))]
      (is (= {:left 1 :right 1 :right-default 1} @times))))
  ;; :query-params schema only evaluates at initialization time
  (testing ":query-params"
    (let [times (atom {:left 0 :right 0 :right-default 0})
          g (str (gensym))
          route (sut/ANY "*" []
                         :query-params [left :- (do (swap! times update :left inc)
                                                    (describe s/Str "wait for created entities to be available for search"))
                                        {right :- (do (swap! times update :right inc)
                                                      (describe s/Bool "wait for created entities to be available for search"))
                                         (do (swap! times update :right-default inc)
                                             :default)}]
                         {:status 200
                          :body [g left right]})
          _ (is (= {:left 1 :right 1 :right-default 1} @times))
          _ (dotimes [_ 10]
              (let [left (str (rand-nth [true false]))
                    right (rand-nth [true false])]
                (is (= [g left right] (:body ((:handler route) {:uri (str "/foo?left=" left "&right=" right)
                                                                :query-params {:left left :right right}}))))))]
      (is (= {:left 1 :right 1 :right-default 1} @times))))
  ;; :middleware only evaluates at initialization time
  (testing ":middleware"
    (let [g (str (gensym))
          times (atom {:initialized-outer 0
                       :initialized-inner 0
                       :called 0})
          route (sut/ANY "*" []
                         :middleware [(do (swap! times update :initialized-outer inc)
                                          (fn [handler]
                                            (swap! times update :initialized-inner inc)
                                            (fn [req]
                                              (swap! times update :called inc)
                                              (handler req))))]
                         {:status 200
                          :body g})
          _ (is (= {:initialized-outer 1 :initialized-inner 1 :called 0} @times))
          _ (dotimes [_ 10]
              (is (= g (:body ((:handler route) {:uri "/"})))))]
      (is (= {:initialized-outer 1 :initialized-inner 1 :called 10} @times))))
  ;; :middleware only evaluates at initialization time
  (testing ":responses"
    (let [g (str (gensym))
          times (atom {404 0 401 0})
          route (sut/ANY "*" []
                         :responses {404 {:schema (do (swap! times update 404 inc) s/Any)}
                                     401 {:schema (do (swap! times update 401 inc) s/Any)}}
                         {:status (rand-nth [404 401])
                          :body g})
          _ (is (= {404 1 401 1} @times))
          _ (dotimes [_ 10]
              (is (= g (:body ((:handler route) {:uri "/"})))))]
      (is (= {404 1 401 1} @times)))))

(defn benchmark []
  (let [sleep (fn []
                (do (println "\tSleeping...")
                    (Thread/sleep 100)
                    (println "\tDone sleeping")))
        g (str (gensym))
        _ (println "vvvvvvvvvvvvvvvvvvvvvv")
        _ (println "Initializing route...")
        route (time
                (sut/POST "*" []
                          :body [body (do (sleep) s/Any)]
                          {:status 200
                           :body g}))
        _ (println "Done initializing route")
        _ (println "^^^^^^^^^^^^^^^^^^^^^^")]
    (println "vvvvvvvvvvvvvvvvvvvvvv")
    (println "Calling route...")
    (time
      (dotimes [i 10]
        (println "Call" i)
        (assert (= g (:body ((:handler route)
                             {:request-method :post :uri "/"}))))))
    (println "Done calling route")
    (println "^^^^^^^^^^^^^^^^^^^^^^"))
  :ok)

(comment (benchmark))
;=vvvvvvvvvvvvvvvvvvvvvv
;=Initializing route...
;=	Sleeping...
;=	Done sleeping
;="Elapsed time: 101.4965 msecs"
;=Done initializing route
;=^^^^^^^^^^^^^^^^^^^^^^
;=vvvvvvvvvvvvvvvvvvvvvv
;=Calling route...
;=Call 0
;=Call 1
;=Call 2
;=Call 3
;=Call 4
;=Call 5
;=Call 6
;=Call 7
;=Call 8
;=Call 9
;="Elapsed time: 1.025375 msecs"
;=Done calling route
;=^^^^^^^^^^^^^^^^^^^^^^

(deftest run-benchmarks
  ;; time out after 500ms. should take about 120ms, but takes 1s if the optimization it tests is wrong.
  (is (deref (future (benchmark)) 500 false)))
