(ns url-shortener.shared.sse-fragments)


(defn sparkline [daily-map]
  (let [days   (sort (keys daily-map))
        values (map #(parse-long (get daily-map %)) days)
        max-v  (apply max 1 values)]
    [:div.sparkline
     (for [[day v] (map vector days values)]
       [:div.sparkline__bar
        {:style (str "--pct:" (int (* 100 (/ v max-v))))
         :title (str day ": " v)}])]))
