package com.mugloar.dragons.ads;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * The two ciphers the API applies to ads, selected by the numeric {@code encrypted} flag.
 *
 * <p>The cipher covers {@code adId}, {@code message} and {@code probability} together;
 * {@code reward}, {@code expiresIn} and {@code encrypted} itself are always plain.
 *
 * <p>Both directions are here because the offline world has to produce encrypted ads that the
 * board decodes back, which makes the round trip testable rather than asserted.
 */
public enum AdCipher {

    NONE(null) {
        @Override
        String decode(String text) {
            return text;
        }

        @Override
        public String encode(String text) {
            return text;
        }
    },

    BASE64(1) {
        @Override
        String decode(String text) {
            try {
                return new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                throw new AdDecodingException("Not valid Base64: " + text, e);
            }
        }

        @Override
        public String encode(String text) {
            return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
        }
    },

    ROT13(2) {
        @Override
        String decode(String text) {
            return rotate(text);
        }

        /** ROT13 is its own inverse. */
        @Override
        public String encode(String text) {
            return rotate(text);
        }
    };

    private final Integer code;

    AdCipher(Integer code) {
        this.code = code;
    }

    /** The value the API puts in {@code encrypted}; {@code null} for plaintext. */
    public Integer code() {
        return code;
    }

    abstract String decode(String text);

    public abstract String encode(String text);

    private static String rotate(String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c >= 'a' && c <= 'z') {
                chars[i] = (char) ('a' + (c - 'a' + 13) % 26);
            } else if (c >= 'A' && c <= 'Z') {
                chars[i] = (char) ('A' + (c - 'A' + 13) % 26);
            }
        }
        return new String(chars);
    }

    /**
     * {@code null} means plaintext, 1 is Base64, 2 is ROT13. Anything else is a cipher this build
     * predates, and guessing at it would put a wrong {@code adId} on the wire.
     */
    static AdCipher forCode(Integer code) {
        if (code == null) {
            return NONE;
        }
        return switch (code) {
            case 1 -> BASE64;
            case 2 -> ROT13;
            default -> throw new AdDecodingException("Unknown cipher code: " + code);
        };
    }
}
