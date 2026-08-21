package com.mugloar.dragons.mugloar;

import com.mugloar.dragons.mugloar.dto.AdResponse;
import com.mugloar.dragons.mugloar.dto.GameStartedResponse;
import com.mugloar.dragons.mugloar.dto.PurchaseResponse;
import com.mugloar.dragons.mugloar.dto.ReputationResponse;
import com.mugloar.dragons.mugloar.dto.ShopItemResponse;
import com.mugloar.dragons.mugloar.dto.SolveResponse;
import com.mugloar.dragons.mugloar.exception.GameNotFoundException;
import com.mugloar.dragons.mugloar.exception.GameOverException;
import com.mugloar.dragons.mugloar.exception.InvalidActionException;
import com.mugloar.dragons.mugloar.exception.MugloarException;
import com.mugloar.dragons.mugloar.exception.MugloarProtocolException;
import com.mugloar.dragons.mugloar.exception.MugloarUnavailableException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryOperations;
import org.springframework.core.retry.Retryable;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link MugloarClient} over Spring's {@code RestClient}.
 *
 * <p>Two rules shape this class. Upstream errors are mostly HTML rather than JSON, so status is
 * inspected before any attempt to parse. And only idempotent calls are retried: {@code solve},
 * {@code buy} and {@code investigateReputation} each consume a turn, so a request that timed out
 * may already have landed and repeating it would silently cost a second turn.
 */
public class RestMugloarClient implements MugloarClient {

    private static final Logger log = LoggerFactory.getLogger(RestMugloarClient.class);

    /** Upstream error bodies are full HTML pages; only the first line is worth carrying. */
    private static final int BODY_SNIPPET_LIMIT = 200;

    private static final ParameterizedTypeReference<List<AdResponse>> AD_LIST =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<ShopItemResponse>> SHOP_ITEM_LIST =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final RetryOperations retry;

    public RestMugloarClient(RestClient restClient, RetryOperations retry) {
        this.restClient = restClient;
        this.retry = retry;
    }

    @Override
    public GameStartedResponse startGame() {
        // Retried despite being a POST: the worst case is an abandoned game, which costs nothing.
        return retrying("startGame", () ->
                restClient.post().uri("/game/start").retrieve().body(GameStartedResponse.class));
    }

    @Override
    public List<AdResponse> listAds(String gameId) {
        return retrying("listAds", () ->
                restClient.get().uri("/{gameId}/messages", gameId).retrieve().body(AD_LIST));
    }

    @Override
    public SolveResponse solve(String gameId, String adId) {
        return once(() -> restClient.post()
                .uri("/{gameId}/solve/{adId}", gameId, adId)
                .retrieve()
                .body(SolveResponse.class));
    }

    @Override
    public List<ShopItemResponse> listShopItems(String gameId) {
        return retrying("listShopItems", () ->
                restClient.get().uri("/{gameId}/shop", gameId).retrieve().body(SHOP_ITEM_LIST));
    }

    @Override
    public PurchaseResponse buy(String gameId, String itemId) {
        return once(() -> restClient.post()
                .uri("/{gameId}/shop/buy/{itemId}", gameId, itemId)
                .retrieve()
                .body(PurchaseResponse.class));
    }

    @Override
    public ReputationResponse investigateReputation(String gameId) {
        return once(() -> restClient.post()
                .uri("/{gameId}/investigate/reputation", gameId)
                .retrieve()
                .body(ReputationResponse.class));
    }

    private <T> T retrying(String name, Supplier<T> call) {
        try {
            return retry.execute(new Retryable<T>() {
                @Override
                public T execute() {
                    return once(call);
                }

                @Override
                public String getName() {
                    return name;
                }
            });
        } catch (RetryException e) {
            Throwable last = e.getLastException();
            if (last instanceof MugloarException mugloarFailure) {
                throw mugloarFailure;
            }
            throw new MugloarUnavailableException(
                    "Mugloar call '%s' failed after %d attempt(s)".formatted(name, e.getRetryCount()),
                    null,
                    last);
        }
    }

    /** Runs the call once, translating every transport-level failure into our own hierarchy. */
    private <T> T once(Supplier<T> call) {
        try {
            T body = call.get();
            if (body == null) {
                throw new MugloarProtocolException("Mugloar returned an empty body", null, null);
            }
            return body;
        } catch (MugloarException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new MugloarUnavailableException("Mugloar is unreachable", null, e);
        } catch (RestClientException e) {
            // A body that started arriving and then broke off is an availability problem, not a
            // contract one: Spring's read timeout on the JDK client is a deadline for the whole
            // response, and firing it closes the stream mid-parse. Telling the two apart by the
            // presence of an IOException keeps that case retryable, while genuinely malformed JSON
            // — which fails with a parse error and no IOException — stays non-retryable.
            if (hasCause(e, IOException.class)) {
                throw new MugloarUnavailableException(
                        "Mugloar stopped responding while its body was being read", null, e);
            }
            throw new MugloarProtocolException("Mugloar returned an unreadable body", null, e);
        }
    }

    private static boolean hasCause(Throwable thrown, Class<? extends Throwable> type) {
        for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    /**
     * Maps an error status onto our exception hierarchy. Registered as the client's default status
     * handler so the mapping exists in exactly one place.
     */
    static void handleErrorStatus(HttpStatusCode status, String bodySnippet) {
        log.debug("Mugloar returned {}: {}", status, bodySnippet);
        throw switch (status.value()) {
            case 400 -> new InvalidActionException("Mugloar rejected the action: " + bodySnippet);
            case 404 -> new GameNotFoundException("Mugloar does not know this game: " + bodySnippet);
            case 410 -> new GameOverException("The game is over");
            default -> status.is5xxServerError()
                    ? new MugloarUnavailableException(
                            "Mugloar failed with " + status + ": " + bodySnippet, status.value())
                    : new MugloarException(
                            "Unexpected response from Mugloar: " + status + ": " + bodySnippet,
                            status.value());
        };
    }

    static String snippet(byte[] body) {
        String text = new String(body, StandardCharsets.UTF_8).replaceAll("\\s+", " ").strip();
        return text.length() <= BODY_SNIPPET_LIMIT
                ? text
                : text.substring(0, BODY_SNIPPET_LIMIT) + "…";
    }

    static String readBodySafely(java.io.InputStream body) {
        try {
            return snippet(body.readAllBytes());
        } catch (IOException e) {
            return "<unreadable body>";
        }
    }
}
