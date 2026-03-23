(ns url-shortener.shared.utils
  (:require [taoensso.carmine :as redis]
            [url-shortener.schema :refer [report-key]]
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

(def hash-url (comp (partial format "%x")
                 #(Murmur3/hashUnencodedChars %)))

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
