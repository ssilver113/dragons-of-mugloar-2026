package com.mugloar.dragons.ads;

import com.mugloar.dragons.mugloar.dto.AdResponse;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Samples are taken verbatim from ads recorded off the live API. */
class DecodedAdTest {

    static Stream<Arguments> recordedAds() {
        return Stream.of(
                Arguments.of("plaintext",
                        new AdResponse("LTyNBlYB", "Help Robin Webster to steal a shipment of gold",
                                15, 7, null, "Piece of cake"),
                        "LTyNBlYB", "Help Robin Webster to steal a shipment of gold", "Piece of cake", false),
                Arguments.of("base64",
                        new AdResponse("c2hFRmNq", "SGVscCBTaGVsYSBUb3duc2VuZCB0byBmaW5kIGEgbG9zdCBjYXQ=",
                                42, 5, 1, "UXVpdGUgbGlrZWx5"),
                        "shEFcj", "Help Shela Townsend to find a lost cat", "Quite likely", true),
                Arguments.of("rot13",
                        new AdResponse("nsWuOGxk", "Uryc Nyna Pnfr gb qrsraq gur ivyyntr",
                                180, 2, 2, "Fhvpvqr zvffvba"),
                        "afJhBTkx", "Help Alan Case to defend the village", "Suicide mission", true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("recordedAds")
    void decodesEveryCipherTheApiUses(
            String name, AdResponse wire, String adId, String message, String label, boolean encrypted) {
        DecodedAd decoded = DecodedAd.from(wire);

        assertThat(decoded.adId()).isEqualTo(adId);
        assertThat(decoded.message()).isEqualTo(message);
        assertThat(decoded.probabilityLabel()).isEqualTo(label);
        assertThat(decoded.encrypted()).isEqualTo(encrypted);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("recordedAds")
    void leavesTheUnencodedFieldsAlone(
            String name, AdResponse wire, String adId, String message, String label, boolean encrypted) {
        DecodedAd decoded = DecodedAd.from(wire);

        assertThat(decoded.reward()).isEqualTo(wire.reward());
        assertThat(decoded.expiresIn()).isEqualTo(wire.expiresIn());
    }

    @Test
    void leavesPlaintextAloneEvenWhenItWouldDecodeAsBase64() {
        // "Impossible" is valid Base64; only the flag decides, never the shape of the text.
        AdResponse wire = new AdResponse("Rk9PQkFS", "Steal the crown", 30, 4, null, "Impossible");

        DecodedAd decoded = DecodedAd.from(wire);

        assertThat(decoded.adId()).isEqualTo("Rk9PQkFS");
        assertThat(decoded.probabilityLabel()).isEqualTo("Impossible");
    }

    @Test
    void rot13LeavesDigitsAndPunctuationUntouched() {
        AdResponse wire = new AdResponse("no123PQ", "Uryc: 42!", 10, 3, 2, "Evfxl");

        DecodedAd decoded = DecodedAd.from(wire);

        assertThat(decoded.adId()).isEqualTo("ab123CD");
        assertThat(decoded.message()).isEqualTo("Help: 42!");
        assertThat(decoded.probabilityLabel()).isEqualTo("Risky");
    }

    @Test
    void refusesACipherItDoesNotKnow() {
        AdResponse wire = new AdResponse("abc", "def", 10, 3, 3, "ghi");

        assertThatThrownBy(() -> DecodedAd.from(wire))
                .isInstanceOf(AdDecodingException.class)
                .hasMessageContaining("3");
    }

    @Test
    void refusesABodyThatIsNotTheBase64ItClaimsToBe() {
        AdResponse wire = new AdResponse("not base64!!", "x", 10, 3, 1, "y");

        assertThatThrownBy(() -> DecodedAd.from(wire)).isInstanceOf(AdDecodingException.class);
    }
}
