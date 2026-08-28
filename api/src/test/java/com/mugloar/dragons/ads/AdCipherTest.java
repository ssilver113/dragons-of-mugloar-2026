package com.mugloar.dragons.ads;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class AdCipherTest {

    private static final String AD_ID = "LTyNBlYB";
    private static final String MESSAGE = "Help Robin Webster to steal 3 apples. Reward: 15 gold!";

    @ParameterizedTest
    @EnumSource(AdCipher.class)
    void encodingAndDecodingAreInverses(AdCipher cipher) {
        assertThat(cipher.decode(cipher.encode(MESSAGE))).isEqualTo(MESSAGE);
        assertThat(cipher.decode(cipher.encode(AD_ID))).isEqualTo(AD_ID);
    }

    @ParameterizedTest
    @EnumSource(AdCipher.class)
    void theCodeRoundTripsBackToItsCipher(AdCipher cipher) {
        assertThat(AdCipher.forCode(cipher.code())).isEqualTo(cipher);
    }

    @Test
    void rot13LeavesDigitsAlone() {
        assertThat(AdCipher.ROT13.encode("abc123XYZ")).isEqualTo("nop123KLM");
    }
}
