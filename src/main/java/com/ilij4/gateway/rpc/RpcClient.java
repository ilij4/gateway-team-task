package com.ilij4.gateway.rpc;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpstreamClient {
    private final WebClient client;
    private final String upstreamUrl;
    private final int timeoutMs;

    private static final Logger log = LoggerFactory.getLogger(UpstreamClient.class);


    public UpstreamClient(Vertx vertx, String upstreamUrl, int timeoutMs) {
        this.upstreamUrl = upstreamUrl;
        this.timeoutMs = timeoutMs;

        HttpClientOptions httpOpts = new HttpClientOptions()
                .setKeepAlive(true)
                .setConnectTimeout(5_000)
                .setIdleTimeout(15);

        this.client = WebClient.wrap(vertx.createHttpClient(httpOpts), new WebClientOptions());
    }

    public Future<Buffer> forward(Buffer body) {
        log.info("Sending request to {} ({} bytes)", upstreamUrl, body.toString());

        return client.postAbs(upstreamUrl)
                .timeout(timeoutMs)
                .putHeader("Content-Type", "application/json")
                .sendBuffer(body)
                .compose(resp -> {
                    int sc = resp.statusCode();
                    if (sc >= 200 && sc < 300) {
                        Buffer b = resp.bodyAsBuffer();
                        return Future.succeededFuture(b != null ? b : Buffer.buffer("null"));
                    } else {
                        String responseBody = null;
                        Buffer b = resp.bodyAsBuffer();
                        if (b != null) responseBody = b.toString();
                        return Future.failedFuture(new UpstreamException(sc, responseBody));
                    }
                });
    }

    public static class UpstreamException extends RuntimeException {
        private final int status;
        private final String body;

        public UpstreamException(int status, String body) {
            super("Upstream status " + status);
            this.status = status;
            this.body = body;
        }
        public int status() { return status; }
        public String body() { return body; }
    }

}
