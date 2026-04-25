(ns url-shortener.shared.utils
  (:require [taoensso.carmine :as redis]
            [url-shortener.schema :refer [report-key TTL-REPORT]]
            [clojure.string :as str])
  (:import org.apache.commons.validator.routines.InetAddressValidator
           org.apache.commons.validator.routines.UrlValidator
           clojure.lang.Murmur3
           java.security.SecureRandom
           java.util.Base64
           java.time.Instant
           java.time.LocalDate))

(def ip-validator  (InetAddressValidator/getInstance))
(def url-validator (UrlValidator. (into-array ["http" "https"])))

(defn hash-url
  ([s]
   (format "%x" (Murmur3/hashUnencodedChars s)))
  ([url owner-id]
    (hash-url (str url owner-id))))

(defn resolve-report [token]
  (let [r (redis/wcar nil (redis/hgetall (report-key token)))]
    (when (seq r)
      (apply hash-map r))))

(defn generate-token []
  (let [bytes (byte-array 16)]
    (.nextBytes (SecureRandom.) bytes)
    (.encodeToString (Base64/getUrlEncoder) bytes)))

(defn epoch-now []
  (.getEpochSecond (Instant/now)))

(defn today [] (str (LocalDate/now)))

(defn this-week []
  (let [date (java.time.LocalDate/now)]
    (format "%d-W%02d"
            (.get date java.time.temporal.IsoFields/WEEK_BASED_YEAR)
            (.get date java.time.temporal.IsoFields/WEEK_OF_WEEK_BASED_YEAR))))

(defn this-month [] (str (.getYear (java.time.LocalDate/now)) "-"
                          (format "%02d" (.getMonthValue (java.time.LocalDate/now)))))

(defn display-name [group-id]
  (last (str/split group-id #":")))

(defn infer-report-type [subject]
  (if (str/includes? subject ":")
    "group"
    "link"))

(defn find-or-create-report! [subject reports-key-fn]
  (let [existing-token (redis/wcar nil (redis/srandmember (reports-key-fn subject)))
        valid-token    (when existing-token
                         (when (pos? (redis/wcar nil (redis/exists (report-key existing-token))))
                           existing-token))]
    (when (and existing-token (not valid-token))
      (redis/wcar nil (redis/srem (reports-key-fn subject) existing-token)))
    (or valid-token
        (let [token (generate-token)]
          (redis/wcar nil
            (redis/hset   (report-key token) "subject" subject "created" (epoch-now))
            (redis/expire (report-key token) TTL-REPORT)
            (redis/sadd   (reports-key-fn subject) token))
          token))))

(defn dev? []
  (if-let [port (System/getProperty "http.port")]
    (= (Integer. port) 8088)
    false))

(defn guest? [owner-id]
  (str/starts-with? owner-id "anon:"))
