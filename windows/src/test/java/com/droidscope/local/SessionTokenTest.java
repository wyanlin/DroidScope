package com.droidscope.local;

public final class SessionTokenTest {
    public static void main(String[] args) {
        String first = SessionToken.create();
        String second = SessionToken.create();

        if (first.length() < 40) throw new AssertionError("token must contain at least 256 bits");
        if (!first.matches("[A-Za-z0-9_-]+")) throw new AssertionError("token must be URL safe");
        if (first.equals(second)) throw new AssertionError("tokens must be unique");
    }
}
