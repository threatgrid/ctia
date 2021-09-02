(ns ctia.frontend.colors)

(def pallette
  ["#E2CFC4"
   "#F7D9C4"
   "#FAEDCB"
   "#C9E4DE"
   "#C6DEF1"
   "#DBCDF0"
   "#F2C6DE"
   "#F9C6C9"
   "#84E3C8"
   "#A8E6CF"
   "#DCEDC1"
   "#FFD3B6"
   "#FFAAA5"
   "#FF8B94"
   "#FF7480"
   "#C670FF"
   "#FFE047"
   "#EEC7FC"])

(defn random []
  (get pallette (rand-int (count pallette))))
