(ns url-shortener.shared.analytics
  (:require
    [taoensso.carmine :as redis]
    [url-shortener.schema :refer
     [group-key group-links-key group-ips-key group-daily-key
      group-countries-key group-platforms-key
      ips-key daily-key countries-key platforms-key platforms-daily-key group-platforms-daily-key
      referrers-key targets-key]]))

;; ---------------------------------------------------------------------------
;; Helpers
;; ---------------------------------------------------------------------------

(defn- parse-counts [raw]
  (if (seq raw)
    (update-vals (apply hash-map raw) parse-long)
    {}))

(defn- hgetall->map [key]
  (parse-counts (redis/wcar nil (redis/hgetall key))))

;; ---------------------------------------------------------------------------
;; Link builders
;; ---------------------------------------------------------------------------

(defn link-stats [path]
  (let [[clicks unique-ips]
        (redis/wcar nil
          (redis/hget  path "clicks")
          (redis/zcard (ips-key path)))]
    {:total_clicks    (parse-long (or clicks "0"))
     :unique_visitors (or unique-ips 0)}))

(defn link-daily [path]
  {"all" (hgetall->map (daily-key path))})

(defn link-countries [path]
  (hgetall->map (countries-key path)))

(defn link-targets [path]
  (set (redis/wcar nil (redis/smembers (targets-key path)))))

(defn link-platforms [path]
  (let [platforms (hgetall->map (platforms-key path))
        targets   (link-targets path)]
    {:platforms platforms
     :confirmed (when (seq targets)
                  (select-keys platforms targets))}))

(defn link-referrers [path]
  (redis/wcar nil (redis/lrange (referrers-key path) 0 19)))

;; ---------------------------------------------------------------------------
;; Group builders
;; ---------------------------------------------------------------------------

(defn group-stats [group-id]
  (let [[clicks unique-ips link-count]
        (redis/wcar nil
          (redis/hget  (group-key group-id) "clicks")
          (redis/zcard (group-ips-key group-id))
          (redis/scard (group-links-key group-id)))]
    {:total_clicks    (parse-long (or clicks "0"))
     :unique_visitors (or unique-ips 0)
     :links           (or link-count 0)}))

(defn group-daily [group-id]
  {"all" (hgetall->map (group-daily-key group-id))})

(defn group-countries [group-id]
  (hgetall->map (group-countries-key group-id)))

(defn group-links [group-id]
  (let [paths   (redis/wcar nil (redis/smembers (group-links-key group-id)))
        results (when (seq paths)
                  (redis/wcar nil
                    (doseq [p paths]
                      (redis/hget p "url")
                      (redis/hget p "description")
                      (redis/hget p "clicks"))))]
    (->> (map vector paths (partition 3 results))
         (map (fn [[path [url desc clicks]]]
                {:path   path
                 :url    url
                 :desc   (or desc "")
                 :clicks (parse-long (or clicks "0"))}))
         (sort-by :clicks >)
         vec)))

(defn group-platforms [group-id]
  (let [platforms (hgetall->map (group-platforms-key group-id))
        ;; aggregate targets across all links — one pipeline call
        paths     (redis/wcar nil (redis/smembers (group-links-key group-id)))
        targets   (when (seq paths)
                    (->> (redis/wcar nil
                           (doseq [p paths]
                             (redis/smembers (targets-key p))))
                         (apply concat)
                         set))]
    {:platforms platforms
     :confirmed (when (seq targets)
                  (select-keys platforms targets))}))

;; ---------------------------------------------------------------------------
;; Global builders (admin dashboard)
;; ---------------------------------------------------------------------------

(defn global-stats []
  (let [group-ids   (redis/wcar nil (redis/smembers "all-groups"))
        total-links (redis/wcar nil (redis/scard "all-links"))
        results     (redis/wcar nil
                      (doseq [g group-ids]
                        (redis/hget  (group-key g) "clicks")
                        (redis/zcard (group-ips-key g))))
        clicks      (->> results (take-nth 2) (map #(parse-long (or % "0"))))
        unique-ips  (->> results (drop 1) (take-nth 2) (map #(or % 0)))]
    {:total_clicks    (apply + clicks)
     :unique_visitors (apply + unique-ips)
     :groups          (count group-ids)
     :links           (or total-links 0)}))

(defn global-groups []
  (->> (redis/wcar nil (redis/smembers "all-groups"))
       (map (fn [group-id]
              (let [[clicks unique-ips link-count]
                    (redis/wcar nil
                      (redis/hget  (group-key group-id) "clicks")
                      (redis/zcard (group-ips-key group-id))
                      (redis/scard (group-links-key group-id)))]
                {:group_id    group-id
                 :clicks      (parse-long (or clicks "0"))
                 :unique_ips  (or unique-ips 0)
                 :link_count  (or link-count 0)})))
       (sort-by :clicks >)
       vec))

(defn global-countries []
  (->> (redis/wcar nil (redis/smembers "all-links"))
       (map (fn [h]
              (hgetall->map (countries-key h))))
       (reduce (fn [acc m] (merge-with + acc m)) {})
       (sort-by val >)
       (take 10)
       (into {})))

(defn link-signals [path]
  (let [{:keys [platforms confirmed]} (link-platforms path)]
    (cond-> {:stats     (link-stats path)
             :countries (link-countries path)
             :platforms platforms
             :daily     (link-daily path)
             :feed      []}
      (seq confirmed) (assoc :confirmed confirmed))))

(defn group-signals [group-id]
  (let [{:keys [platforms confirmed]} (group-platforms group-id)]
    (cond-> {:stats     (group-stats group-id)
             :countries (group-countries group-id)
             :platforms platforms
             :daily     (group-daily group-id)
             :links     (group-links group-id)
             :feed      []}
      (seq confirmed) (assoc :confirmed confirmed))))

(defn link-daily [path]
  (let [known-platforms (keys (hgetall->map (platforms-key path)))]
    (into {"all" (hgetall->map (daily-key path))}
          (map (fn [p] [p (hgetall->map (platforms-daily-key path p))]))
          known-platforms)))

(defn group-daily [group-id]
  (let [known-platforms (keys (hgetall->map (group-platforms-key group-id)))]
    (into {"all" (hgetall->map (group-daily-key group-id))}
          (map (fn [p] [p (hgetall->map (group-platforms-daily-key group-id p))]))
          known-platforms)))
