(ns ctia.lib.compojure.api.core-test
  (:require [ctia.lib.compojure.api.core :as sut]
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

;; adapted from clojure.repl/root-cause, but unwraps compiler exceptions
(defn root-cause [t]
  (loop [cause t]
    (if-let [cause (.getCause cause)]
      (recur cause)
      cause)))

(defn is-context-banned [form msg]
  (try (dexpand-1 form)
       (is false (pr-str form))
       (catch Exception e
         (is (= msg (ex-message (root-cause e))) (pr-str form)))))

(deftest context-banned-test
  (is-context-banned
    `(sut/context
       "/my-route" []
       :path-params [~'id :- s/Str]
       ~'routes)
    "Not allowed these options in `context`, push into HTTP verbs instead: (:path-params)")
  (is-context-banned
    `(sut/context
       "/my-route" []
       :query-params [{~'wait_for :- (describe s/Bool "wait for patched entity to be available for search") nil}]
       ~'routes)
    "Not allowed these options in `context`, push into HTTP verbs instead: (:query-params)")
  (is-context-banned
    `(sut/context
       "/my-route" []
       :auth-identity ~'identity
       ~'routes)
    "Not allowed these options in `context`, push into HTTP verbs instead: (:auth-identity)")
  (is-context-banned
    `(sut/context
       "/my-route" []
       :identity-map ~'identity-map
       ~'routes)
    "Not allowed these options in `context`, push into HTTP verbs instead: (:identity-map)"))
 
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
;vvvvvvvvvvvvvvvvvvvvvv
;Initializing route...
;	Sleeping...
;	Done sleeping
;"Elapsed time: 106.062042 msecs"
;Done initializing route
;^^^^^^^^^^^^^^^^^^^^^^
;vvvvvvvvvvvvvvvvvvvvvv
;Calling route...
;Call 0
;	Sleeping...
;	Done sleeping
;Call 1
;	Sleeping...
;	Done sleeping
;Call 2
;	Sleeping...
;	Done sleeping
;Call 3
;	Sleeping...
;	Done sleeping
;Call 4
;	Sleeping...
;	Done sleeping
;Call 5
;	Sleeping...
;	Done sleeping
;Call 6
;	Sleeping...
;	Done sleeping
;Call 7
;	Sleeping...
;	Done sleeping
;Call 8
;	Sleeping...
;	Done sleeping
;Call 9
;	Sleeping...
;	Done sleeping
;"Elapsed time: 1037.317 msecs"
;Done calling route
;^^^^^^^^^^^^^^^^^^^^^^
