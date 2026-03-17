(ns url-shortener.admin.render
  (:require
    [hiccup2.core :as h]
    [taoensso.carmine :as redis]
    [url-shortener.schema :refer [ips-key daily-key countries-key]]))

;; -- Helpers ------------------------------------------------------------------


(defn- all-link-hashes []
  (sort (redis/wcar nil (redis/smembers "all-links"))))


(defn- sparkline [daily-map]
  (let [days   (sort (keys daily-map))
        values (map #(parse-long (get daily-map %)) days)
        max-v  (apply max 1 values)]
    [:div.sparkline
     (for [[day v] (map vector days values)]
       [:div.sparkline__bar
        {:style (str "--pct:" (int (* 100 (/ v max-v))))
         :title (str day ": " v)}])]))

;; -- Render functions ---------------------------------------------------------

(defn render-stats []
  (let [hashes  (all-link-hashes)
        results (redis/wcar nil
                  (doseq [h hashes]
                    (redis/hget  h "clicks")
                    (redis/zcard (ips-key h))))
        clicks     (->> results (take-nth 2) (map (fnil parse-long "0")))
        unique-ips (->> results (drop 1) (take-nth 2))]
    (str (h/html
      [:div#stats.switcher
       [:div.box.stack
        [:span.stat__label "Total Links"]
        [:span.stat__value (count hashes)]]
       [:div.box.stack
        [:span.stat__label "Total Clicks"]
        [:span.stat__value (apply + clicks)]]
       [:div.box.stack
        [:span.stat__label "Unique Visitors"]
        [:span.stat__value (apply + unique-ips)]]]))))

(defn render-link-card [path]
  (let [[url desc clicks daily]
        (redis/wcar nil
          (redis/hget    path "url")
          (redis/hget    path "description")
          (redis/hget    path "clicks")
          (redis/hgetall (daily-key path)))]
    [:div.box.stack {:style "--stack-space: var(--space-s)"}
     [:div.cluster
      [:span.link-card__clicks (or clicks "0")]
      [:span.stat__label "clicks"]]
     [:div.link-card__url url]
     (when (seq desc)
       [:div.link-card__desc desc])
     (when (seq daily)
       (sparkline (apply hash-map daily)))]))

(defn render-links []
  (str (h/html
    [:div#links.grid
     (map render-link-card (all-link-hashes))])))

(defn render-countries []
  (let [hashes  (all-link-hashes)
        totals  (->> hashes
                     (map (fn [h]
                            (apply hash-map
                              (redis/wcar nil
                                (redis/hgetall (countries-key h))))))
                     (reduce (fn [acc m]
                               (merge-with + acc
                                 (update-vals m parse-long)))
                             {}))
        sorted  (sort-by val > totals)
        max-v   (apply max 1 (vals totals))]
    (str (h/html
      [:div#countries.box.stack
       [:span.stat__label "Top Countries"]
       (for [[country cnt] (take 10 sorted)]
         [:div.country-row
          [:span country]
          [:span cnt]
          [:div.country-row__bar
           {:style (str "--pct:" (int (* 100 (/ cnt max-v))))}]])]))))


