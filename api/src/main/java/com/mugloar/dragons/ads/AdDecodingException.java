package com.mugloar.dragons.ads;

/** An ad carried a cipher we do not know, or a body that would not decode under the one it declared. */
public class AdDecodingException extends RuntimeException {

    public AdDecodingException(String message, Throwable cause) {
        super(message, cause);
    }

    public AdDecodingException(String message) {
        super(message);
    }
}
