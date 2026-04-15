(ns url-shortener.admin.v1.render
  (:require
    [hiccup2.core :as h]
    [taoensso.carmine :as redis]
    [url-shortener.schema :refer [ips-key daily-key countries-key group-key group-links-key group-ips-key]]
    [url-shortener.shared.utils :refer [display-name]]))

(defn- all-group-ids []
  (redis/wcar nil (redis/smembers "all-groups")))

(defn- group-click-count [group-id]
  (parse-long (or (redis/wcar nil (redis/hget (group-key group-id) "clicks")) "0")))

(defn- sorted-groups []
  (->> (all-group-ids)
       (map (fn [group-id]
              (let [[clicks unique-ips link-count]
                    (redis/wcar nil
                      (redis/hget  (group-key group-id) "clicks")
                      (redis/zcard (group-ips-key group-id))
                      (redis/scard (group-links-key group-id)))]
                {:group-id   group-id
                 :name       (display-name group-id)
                 :clicks     (parse-long (or clicks "0"))
                 :unique-ips (or unique-ips 0)
                 :link-count (or link-count 0)})))
       (sort-by :clicks >)))

(defn render-stats []
  (let [groups     (all-group-ids)
        total-links (redis/wcar nil (redis/scard "all-links"))
        results    (redis/wcar nil
                               (doseq [g groups]
                                 (redis/hget (group-key g) "clicks")
                                 (redis/zcard (group-ips-key g))))
        clicks     (->> results (take-nth 2) (map #(parse-long (or % "0"))))
        unique-ips (->> results (drop 1) (take-nth 2) (map #(or % 0)))]
    (str (h/html
      [:div {:id "stats" :class "switcher"}
       [:div.box.stack
        [:span.stat__label "Total Groups"]
        [:span.stat__value (count groups)]]
       [:div.box.stack
        [:span.stat__label "Total Links"]
        [:span.stat__value total-links]]
       [:div.box.stack
        [:span.stat__label "Total Clicks"]
        [:span.stat__value (apply + clicks)]]
       [:div.box.stack
        [:span.stat__label "Unique Visitors"]
        [:span.stat__value (apply + unique-ips)]]]))))

(defn render-groups []
  (let [groups (sorted-groups)]
    (str (h/html
      [:div {:id "groups" :class "stack"}
       [:span.stat__label "Groups"]
       (if (seq groups)
         (for [{:keys [group-id clicks unique-ips link-count]} groups]
           [:a.group-card.box.stack
            {:href                    (str "/admin/v1/group/" group-id)
             :style                   "--stack-space: var(--space-s)"
             :data-on:click           (str "@get('/admin/v1/group/" group-id "')")}
            [:div.cluster
             [:span.group-card__name (display-name group-id)]
             [:span.stat__label (str link-count " links")]]
            [:div.switcher
             [:div.stack
              [:span.stat__label "Clicks"]
              [:span.group-card__clicks clicks]]
             [:div.stack
              [:span.stat__label "Unique Visitors"]
              [:span.group-card__clicks unique-ips]]]])
         [:span.stat__muted "No groups yet"])]))))


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


