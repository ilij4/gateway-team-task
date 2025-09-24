package com.ilij4.gateway.rpc;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RpcClient {
    private final Vertx vertx;
    private final WebClient client;
    private final String rpcUrl;
    private final int timeoutMs;

    private static final Logger log = LoggerFactory.getLogger(RpcClient.class);

    public RpcClient(Vertx vertx, String rpcUrl, int timeoutMs) {
        this.vertx = vertx;
        this.rpcUrl = rpcUrl;
        this.timeoutMs = timeoutMs;

        HttpClientOptions httpOpts = new HttpClientOptions()
                .setKeepAlive(true)
                .setConnectTimeout(5_000)
                .setIdleTimeout(15);

        this.client = WebClient.wrap(vertx.createHttpClient(httpOpts), new WebClientOptions());
    }

    public Future<Buffer> forward(Buffer body) {
        return forward(RpcRequest.of(body).build());
    }

    public Future<Buffer> forward(RpcRequest req) {
        final int effectiveTimeout = req.timeoutMs() != null ? req.timeoutMs() : this.timeoutMs;

        // concise, safe logging (no payload dump)
        log.info("RPC -> {} ({} bytes) corrId={}", rpcUrl, req.body().length(), req.correlationId());

        Promise<Buffer> promise = Promise.promise();
        sendWithRetry(req, effectiveTimeout, 0, promise);
        return promise.future();
    }

    private void sendWithRetry(RpcRequest req, int timeout, int attempt, Promise<Buffer> sink) {
        client.postAbs(rpcUrl)
                .timeout(timeout)
                .putHeader("Content-Type", "application/json")
                .putHeader("User-Agent", "gateway/1.0")
                .sendBuffer(req.body())
                .compose(resp -> {
                    int sc = resp.statusCode();
                    if (sc >= 200 && sc < 300) {
                        Buffer b = resp.bodyAsBuffer();
                        return Future.succeededFuture(b != null ? b : Buffer.buffer("null"));
                    } else {
                        return Future.failedFuture(new UpstreamException(sc, resp.bodyAsString()));
                    }
                })
                .onSuccess(sink::complete)
                .onFailure(err -> {
                    int max = req.maxRetries();
                    if (attempt < max && shouldRetry(err)) {
                        vertx.setTimer(backoffMs(attempt), t -> sendWithRetry(req, timeout, attempt + 1, sink));
                    } else {
                        sink.fail(err);
                    }
                });
    }

    public Future<Void> forwardStreaming(RpcRequest req, io.vertx.core.http.HttpServerResponse out) {
        Promise<Void> p = Promise.promise();
        client.postAbs(rpcUrl)
                .timeout(req.timeoutMs() != null ? req.timeoutMs() : timeoutMs)
                .putHeader("Content-Type", "application/json")
                .sendBuffer(req.body());
        return p.future();
    }

    private boolean shouldRetry(Throwable t) {
        if (t instanceof UpstreamException ue) {
            return ue.status == 429 || ue.status == 502 || ue.status == 503 || ue.status == 504;
        }
        return false;
    }

    private long backoffMs(int attempt) { // jittered exponential-ish
        long base = (long)Math.min(1000 * Math.pow(2, attempt), 4000);
        return base + (long)(Math.random() * 300);
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
