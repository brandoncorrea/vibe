(ns chart.data)

(def photographs-by-period
  "Estimated total photographs taken worldwide, by 5-year period. Values in billions.

   Pre-2000 estimates derive from film consumption (Kodak / Fuji roll shipments
   × ~24 exposures). 2000-onward values are synthesized from industry estimates
   published by InfoTrends, Mylio Photos, and KeyPoint Intelligence, which place
   2023 alone at roughly 1.6 trillion photos."
  [{:label "1976–1980" :value    55}
   {:label "1981–1985" :value    78}
   {:label "1986–1990" :value   100}
   {:label "1991–1995" :value   135}
   {:label "1996–2000" :value   250}
   {:label "2001–2005" :value   750}
   {:label "2006–2010" :value  1500}
   {:label "2011–2015" :value  3500}
   {:label "2016–2020" :value  6500}
   {:label "2021–2025" :value  8500}])
