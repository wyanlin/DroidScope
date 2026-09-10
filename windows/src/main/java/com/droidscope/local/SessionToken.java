package com.droidscope.local;

import java.security.SecureRandom;
import java.util.Base64;

public final class SessionToken {
    private static final SecureRandom RANDOM = new SecureRandom();

    private SessionToken() {}

    public static String create() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
