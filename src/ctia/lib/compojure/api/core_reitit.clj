(ns ctia.lib.compojure.api.core-reitit
  "Exposes the API of compojure.api.core v1.1.13
  
  Always use this namespace over compojure.api.{core,sweet}
  as it also loads the CTIA routing extensions."
  (:require [compojure.api.common :as common]
            [clojure.set :as set]
            [ctia.http.middleware.auth :as mid]))

;; banned
(require '[compojure.api.core :as core])

(assert magic/add-id-to-request
        (str "Never delete the :require of ctia.http.middleware.auth! "
             "It has magic defmethod calls that are crucial to the "
             "reliability of CTIA's startup. "
             "See also: https://github.com/threatgrid/iroh/issues/4458"))

;;TODO this isn't right
(defn routes
  "Create a Ring handler by combining several handlers into one."
  [& handlers]
  (vec handlers))

(defn undocumented
  "Routes without route-documentation. Can be used to wrap routes,
  not satisfying compojure.api.routes/Routing -protocol."
  [& handlers]
  (apply core/undocumented handlers))

(defmacro middleware
  "Wraps routes with given middlewares using thread-first macro.
  Note that middlewares will be executed even if routes in body
  do not match the request uri. Be careful with middlewares that
  have side-effects."
  {:style/indent 1}
  [middleware & body]
  `["" {:middleware ~middleware}
    [~@body]])

(def ^:private allowed-context-options #{:tags :capabilities :description :return :summary})

(def ^:private ^:dynamic *gensym* gensym)

(defmacro context
  "Like compojure.api.core/context, except the binding vector must be empty and
  no binding-style options are allowed. This is to prevent the passed routes
  from being reinitialized on every request."
  {:style/indent 2}
  [path arg & args]
  (assert (vector? arg))
  (assert (= [] arg) (str "Not allowed to bind anything in context, push into HTTP verbs instead: " (pr-str arg)))
  (let [[options body] ((requiring-resolve 'compojure.api.common/extract-parameters) args true)
        _ (when-some [extra-keys (not-empty (set/difference (set (keys options))
                                                            allowed-context-options))]
            (throw (ex-info (str "Not allowed these options in `context`, push into HTTP verbs instead: "
                                 (pr-str (sort extra-keys)))
                            {})))
        reitit-opts (cond-> {}
                      (:tags options) (assoc-in [:swagger :tags] (list 'quote (:tags options)))
                      (:description options) (assoc-in [:swagger :description] (:description options))
                      (:summary options) (assoc-in [:swagger :summary] (:summary options))
                      (:capabilities options) (update :middleware (fnil conj [])
                                                      [`mid/wrap-capabilities (:capabilities options)])
                      (:return options) (assoc-in [:responses :default] {:schema (:return options)}))]
    `[~path
      ~@(some-> (not-empty reitit-opts) list)
      (routes ~@body)]))

(defmacro GET     {:style/indent 2} [& args] `(core/GET ~@args))
(defmacro ANY     {:style/indent 2} [& args] `(core/ANY ~@args))
(defmacro HEAD    {:style/indent 2} [& args] `(core/HEAD ~@args))
(defmacro PATCH   {:style/indent 2} [& args] `(core/PATCH ~@args))
(defmacro DELETE  {:style/indent 2} [& args] `(core/DELETE ~@args))
(defmacro OPTIONS {:style/indent 2} [& args] `(core/OPTIONS ~@args))
(defmacro POST    {:style/indent 2} [& args] `(core/POST ~@args))
(defmacro PUT     {:style/indent 2} [& args] `(core/PUT ~@args))
