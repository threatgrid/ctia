(ns ctia.flows.hooks
  "Handle hooks (Cf. #159).")

(def default-hooks
  {:before-create []
   :before-send-to-event-chan []})

(def hooks (atom default-hooks))

(defn reset-hooks! []
  (reset! hooks default-hooks))

(defprotocol Hook
  "A hook is mainly a function"
  (init [this])
  (handle [this
           type-name
           stored-object])
  (destroy [this]))

(defn find-hooks
  "Should return a list of `Hook` in the correct order"
  [hook-name]
  :todo)

(defn add-hook!
  "Add a `Hook` for the hook `hook-name`"
  [hook-name hook]
  (swap! hooks update-in [hook-name] conj hook))

(defn add-hooks!
  "Add a list of `Hook` for the hook `hook-name`"
  [hook-name hook-list]
  (swap! hooks update-in [hook-name] concat hook-list))

(defn init!
  "Initialize hooks"
  []
  (doseq [hook-name (keys @hooks)]
    (let [hook-list (find-hooks hook-name)]
      (add-hooks! hook-name hook-list))))

(defn init-hooks!
  "Should search Jar, Namespaces and init Objects"
  []
  (doseq [hook-name (keys @hooks)]
    (doseq [hook (get @hooks hook-name)]
      (init hook)
      (add-hook! hook-name hook)))
  @hooks)

(defn destroy-hooks
  "Should call all destructor for each hook in reverse order."
  []
  (doseq [hook-name (keys @hooks)]
    (doseq [hook (reverse (get @hooks hook-name))]
      (destroy hook))))

(defn bind-realized
  "bind high level type would be:

    RealizedObject
    -> (Type -> RealizedObject -> Maybe RealizedObject)
    -> RealizedObject

  If The hook return nil, doens't modify the realized object returned."
  [realized-object hook type-name]
  (let [result (handle hook type-name realized-object)]
    (if (nil? result)
      realized-object
      result)))

(defn apply-hook-list
  "Apply all hooks to some realized object of some type"
  [type-name realized-object hook-list]
  (reduce (fn [acc f]
            (bind-realized acc f type-name)) realized-object hook-list))

(defn apply-hooks
  "Apply all hooks for some hook-name"
  [type-name realized-object hook-name]
  (apply-hook-list type-name realized-object (get @hooks hook-name)))

