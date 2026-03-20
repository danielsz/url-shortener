(ns url-shortener.admin.sanitize
  (:require [taoensso.carmine :as redis]))


(defn populate-all-links! []
  (let [hashes (->> (redis/wcar nil (redis/keys "*"))
                    (filter #(re-matches #"[0-9a-f]+" %)))]
    (when (seq hashes)
      (redis/wcar nil (apply redis/sadd "all-links" hashes)))
    (println "populated all-links with" (count hashes) "keys")))

(defn populate-all-groups! []
  (let [groups (->> (redis/wcar nil (redis/keys "group:*"))
                  (remove #(re-find #":(links|daily|countries|ips|reports)$" %))
                  (map #(subs % 6)))]  ; strip "group:" prefix
  (when (seq groups)
    (redis/wcar nil (apply redis/sadd "all-groups" groups)))
  (println "populated all-groups with" (count groups) "groups")))


(defn inspect-keyspace []
  (let [all-keys (redis/wcar nil (redis/keys "*"))
        grouped  (group-by (fn [k]
                             (cond
                               (re-find #"^/" k)        :legacy
                               (re-find #"^user:" k)    :user
                               (re-find #"^report:" k)  :report
                               (re-find #"^all-links$" k) :index
                               (re-find #":" k)         :analytics
                               (re-matches #"[0-9a-f]+" k) :link
                               :else                    :unknown))
                           all-keys)]
    (doseq [[type keys] (sort-by key grouped)]
      (println type "-" (count keys) "keys")
      (when (= type :unknown)
        (println "  samples:" (take 10 keys))))))


(defn sample-legacy-activity []
  (let [legacy (->> (redis/wcar nil (redis/keys "*"))
                    (filter #(re-find #"^/" %))
                    (take 1000))
        results (redis/wcar nil
                  (doseq [k legacy]
                    (redis/exists k)))]
    (println "sampled 1000 legacy keys, all exist by definition")
    ;; check last access via TTL - no TTL means never expires
    (let [ttls (redis/wcar nil
                 (doseq [k (take 100 legacy)]
                   (redis/ttl k)))]
      (println "TTL samples:" (take 20 ttls)))))

(defn sample-legacy-clicks []
  (let [legacy (->> (redis/wcar nil (redis/keys "/*"))
                    (shuffle)
                    (take 1000))
        results (redis/wcar nil
                  (doseq [k legacy]
                    (redis/get k)))]
    (let [non-nil (remove nil? results)]
      (println "sampled 1000 legacy keys")
      (println "valid URLs:" (count non-nil))
      (println "sample URLs:" (take 3 non-nil)))))
