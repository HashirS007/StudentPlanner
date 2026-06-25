package com.studentplanner.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/* This class functions as the class for password hashing and verification
Uses SHA-256 with per-user random salt, which defeats rainbow table attack.
Salt is basically the random data we are adding to the password
This class has only static methods because it is stateless i.e.
 every call is independent. There is no reason to instantiate it, hence the
 private constructor.
 */

public class PasswordUtil {
    private static final int SALT_BYTES = 16; // Number of random bytes in a salt. 16 bytes = 128 bits, encoded as 32 hex chars.
    private static final SecureRandom RANDOM = new SecureRandom(); //IT is like math.random but Cryptographically secure random number generator.
    private PasswordUtil() {} //prevents someone from instantiating the class
    private static String bytesToHex(byte[] bytes) {
        // Converts a byte array to a lowercase hex string, Each byte becomes exactly 2 hex characters (e.g., 0xAB -> "ab"). DB cant store raw bytes so we make it printable
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            // %02x means: format as hex, lowercase, padded to 2 chars with leading zero
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    public static String generateSalt() { //Generate a fresh, cryptographically random salt. Returns a 32 char hex string for DB storge.
        byte[] saltBytes = new byte[SALT_BYTES];
        RANDOM.nextBytes(saltBytes);
        return bytesToHex(saltBytes);
    }
    public static String hashPassword(String plainPassword, String salt) {
        //hashes a plain text password together with its salt using SHA-256. Return a 64 char hex string
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); //MessageDigest is java built in interface for cryptographic hash functions
            // Mix the salt into the input so identical passwords produce different hashes
            String saltedPassword = salt + plainPassword;
            byte[] hashBytes = digest.digest(saltedPassword.getBytes());
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is part of every standard JVM — this should never happen.
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    public static boolean verifyPassword(String plainPassword, String storedSalt, String storedHash) {
        // Re-hashes the plaintext with the stored salt and compares to the stored hash.
        //storedSalt ref the salt loaded from the DB for this user, storedHash ref the hash loaded from the DB for this user
        String computedHash = hashPassword(plainPassword, storedSalt);
        return computedHash.equals(storedHash);
    }
}

