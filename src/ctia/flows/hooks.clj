(ns ctia.flows.hooks
  "Handle hooks (Cf. #159).")

(def default-hooks
  {:before-create []
   :after-create []
   :before-update []
   :after-update []
   :before-delete []
   :after-delete []})

(def hooks (atom default-hooks))

(defn reset-hooks! []
  (reset! hooks default-hooks))

(defprotocol Hook
  "A hook is mainly a function"
  (init [this])
  (handle [this
           type-name
           stored-object
           prev-object])
  (destroy [this]))

(defn find-hooks
  "Should return a list of `Hook` in the correct order"
  [hook-type]
  :todo)

(defn add-hook!
  "Add a `Hook` for the hook `hook-type`"
  [hook-type hook]
  (swap! hooks update-in [hook-type] conj hook))

(defn add-hooks!
  "Add a list of `Hook` for the hook `hook-type`"
  [hook-type hook-list]
  (swap! hooks update-in [hook-type] concat hook-list))

(defn init!
  "Initialize hooks"
  []
  (doseq [hook-type (keys @hooks)]
    (let [hook-list (find-hooks hook-type)]
      (add-hooks! hook-type hook-list))))

(defn init-hooks!
  "Should search Jar, Namespaces and init Objects"
  []
  (doseq [hook-type (keys @hooks)]
    (doseq [hook (get @hooks hook-type)]
      (init hook)
      (add-hook! hook-type hook)))
  @hooks)

(defn destroy-hooks
  "Should call all destructor for each hook in reverse order."
  []
  (doseq [hook-type (keys @hooks)]
    (doseq [hook (reverse (get @hooks hook-type))]
      (destroy hook))))

(defn bind-realized
  "bind high level type would be:

    RealizedObject
    -> (Type -> RealizedObject -> RealizedObject -> Maybe RealizedObject)
    -> RealizedObject
    -> RealizedObject

  If the hook returns nil, it doens't modify the realized object returned."
  [realized-object hook prev-object type-name]
  (let [result (handle hook type-name realized-object prev-object)]
    (if (nil? result)
      realized-object
      result)))

(defn apply-hook-list
  "Apply all hooks to some realized object of some type"
  [type-name realized-object prev-object hook-list]
  (reduce (fn [acc f]
            (bind-realized acc f prev-object type-name)) realized-object hook-list))

(defn apply-hooks
  "Apply all hooks for some hook-type"
  [type-name realized-object prev-object hook-type]
  (apply-hook-list type-name realized-object prev-object (get @hooks hook-type)))

