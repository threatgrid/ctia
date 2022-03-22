((clojure-mode . ((eval . (progn
                            ;; Define custom indentation for functions inside metabase.
                            ;; This list isn't complete; add more forms as we come across them.
                            ;; Described here:
                            ;;   https://docs.cider.mx/cider/indent_spec.html#_examples
                            (define-clojure-indent
                              ;; defservice service-name [docstring] [protocol] [list-of-protocols] [list-of-methods]
                              (puppetlabs.trapperkeeper.core/defservice '(:defn (:defn)))
                              (trapperkeeper/defservice                 '(:defn (:defn)))
                              (tk/defservice                            '(:defn (:defn)))
                              (defservice                               '(:defn (:defn)))))))))
