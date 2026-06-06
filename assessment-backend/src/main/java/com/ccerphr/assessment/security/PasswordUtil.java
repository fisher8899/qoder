package com.ccerphr.assessment.security;

import java.security.SecureRandom;

public final class PasswordUtil {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "!@#$%";
    private static final String ALL = UPPER + LOWER + DIGITS + SYMBOLS;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() {
    }

    public static String generateTemporaryPassword() {
        char[] password = new char[12];
        password[0] = pick(UPPER);
        password[1] = pick(LOWER);
        password[2] = pick(DIGITS);
        password[3] = pick(SYMBOLS);
        for (int i = 4; i < password.length; i++) {
            password[i] = pick(ALL);
        }
        shuffle(password);
        return new String(password);
    }

    private static char pick(String source) {
        return source.charAt(RANDOM.nextInt(source.length()));
    }

    private static void shuffle(char[] chars) {
        for (int i = chars.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
    }
}
