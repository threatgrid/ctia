(ns ctia.frontend.colors)

(def pallette
  ["#DCEDC1"
   "#E2CFC4"
   "#C9E4DE"
   "#FFAAA5"
   "#DBCDF0"
   "#F2C6DE"
   "#580AFF"
   "#84E3C8"
   "#F7D9C4"
   "#147DF5"
   "#A1FF0A"
   "#FF8B94"
   "#C670FF"
   "#FFE047"
   "#EEC7FC"
   "#FF0000"
   "#FF8700"
   "#A8E6CF"
   "#FAEDCB"
   "#DEFF0A"
   "#FFD300"
   "#C6DEF1"
   "#F9C6C9"
   "#0AFF99"
   "#FF7480"
   "#FFD3B6"
   "#0AEFFF"
   "#BE0AFF"])

(defn get-colors
  "Gets a sequence of colors of length `n` from the pallette"
  [n]
  (vec (take n (cycle pallette))))


