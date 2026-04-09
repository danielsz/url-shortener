(ns url-shortener.admin.sanitize
  (:require [taoensso.carmine :as redis]
            [clojure.string :as str]
            [url-shortener.analytics :refer [referrer->platform]]
            [url-shortener.schema :refer [default-group-id group-links-key referrers-key platforms-key]]))


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


(defn migrate-groupless-link-hashes []
  (let [hashes (redis/wcar nil (redis/smembers "all-links"))
        migrated (atom 0)
        skipped  (atom 0)]
    (doseq [path hashes]
      (let [group-id (redis/wcar nil (redis/hget path "group-id"))]
        (if group-id
          (swap! skipped inc)
          (do
            (redis/wcar nil
                        (redis/hset path "group-id" (default-group-id "anonymous"))
                        (redis/sadd (group-links-key (default-group-id "anonymous")) path)
                        (redis/sadd "all-groups" (default-group-id "anonymous")))
            (swap! migrated inc)))))
    (println "migrated:" @migrated "skipped:" @skipped "total:" (count hashes))))

(defn migrate-report-key []
  (let [report-keys (->> (redis/wcar nil (redis/keys "report:*"))
                       (filter #(not (re-find #"[:].*[:]" %))))]
  (doseq [rk report-keys]
    (let [report (apply hash-map (redis/wcar nil (redis/hgetall rk)))]
      (when (get report "target-id")
        (redis/wcar nil
          (redis/hset rk "subject" (get report "target-id"))
          (redis/hdel rk "target-id")
          (redis/hdel rk "type")
          (redis/hdel rk "owner-id")))))
  (println "migrated" (count report-keys) "report keys")))

(defn referers []
  (let [hashes  (redis/wcar nil (redis/smembers "all-links"))
        refs    (->> hashes
                   (mapcat (fn [path]
                             (redis/wcar nil
                                         (redis/lrange (referrers-key path) 0 -1))))
                   (remove nil?)
                   (remove empty?)
                   distinct
                   sort)]
    (doseq [r refs]
      (println r))
    (println "\ntotal unique referrers:" (count refs))))

(defn clean-up-self-referers []
  (let [hashes (redis/wcar nil (redis/smembers "all-links"))
      removed (atom 0)]
  (doseq [h hashes]
    (let [refs      (redis/wcar nil (redis/lrange (str h ":referrers") 0 -1))
          self-refs (filter #(clojure.string/starts-with? % "https://tuppu.net/") refs)]
      (when (seq self-refs)
        (redis/wcar nil
          (doseq [r self-refs]
            (redis/lrem (str h ":referrers") 0 r)))
        (swap! removed + (count self-refs)))))
  (println "removed" @removed "self-referrer entries")))


(defn breakdown []
  (let [hashes (redis/wcar nil (redis/smembers "all-links"))
      all-refs (->> hashes
                    (mapcat (fn [h]
                              (redis/wcar nil
                                (redis/lrange (str h ":referrers") 0 -1))))
                    (remove nil?))
      by-platform (->> all-refs
                       (map referrer->platform)
                       frequencies)
      total (apply + (vals by-platform))]
  (println (str "total referrers: " total))
  (doseq [[p n] (->> by-platform (sort-by val >))]
  (println (format "%-12s %4d  %.1f%%" p n (* 100.0 (/ n total)))))))
