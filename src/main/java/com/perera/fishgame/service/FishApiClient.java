package com.perera.fishgame.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.perera.fishgame.model.Game;

/**
 * Interoperability: gets games from the external Fish Game web service
 * (https://marcconrad.com/uob/fish) using HTTP GET. The service is written and
 * hosted by someone else, in another technology, and we only agree on the
 * protocol (HTTPS) and the data format (CSV with a Base64 encoded image).
 *
 * <p>Unlike the unit's basic example, this class has a timeout, checks the HTTP
 * status code, retries once and reports problems with a checked exception.
 *
 * <p>Author: Vinod Perera. API usage taken from the Fish Game documentation.
 */
public class FishApiClient implements GameProvider {

    /** Endpoint from the Fish Game API documentation (CSV output, Base64 image). */
    public static final String DEFAULT_URL =
            "https://marcconrad.com/uob/fish/api.php?out=csv&base64=yes";

    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    private final HttpClient http;
    private final URI uri;
    private final int maxAttempts;

    public FishApiClient() {
        this(DEFAULT_URL, 2);
    }

    public FishApiClient(String url, int maxAttempts) {
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        this.uri = URI.create(url);
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Override
    public Game nextGame() throws GameProviderException {
        GameProviderException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return FishApiParser.parse(fetch());
            } catch (GameProviderException e) {
                last = e; // try again, the service sometimes returns an error
            }
        }
        throw last;
    }

    private String fetch() throws GameProviderException {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build();
        try {
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new GameProviderException(
                        "Game service returned HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException e) {
            throw new GameProviderException("Could not reach the game service", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GameProviderException("Request was interrupted", e);
        }
    }
}