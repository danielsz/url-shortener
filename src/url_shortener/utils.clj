(ns url-shortener.utils
  (:import
   java.security.SecureRandom
   java.util.Base64))

(defn generate-token []
  (let [bytes (byte-array 16)]
    (.nextBytes (SecureRandom.) bytes)
    (.encodeToString (Base64/getUrlEncoder) bytes)))

