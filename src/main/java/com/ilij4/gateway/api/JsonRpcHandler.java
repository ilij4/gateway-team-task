package com.ilij4.gateway.http;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.ilij4.gateway.rpc.RpcClient;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;


public class JsonRpcHandler implements Handler<RoutingContext> {
    private final RpcClient upstream;
    private final ConcurrentHashMap<String, LongAdder> counters;
    private final JsonFactory factory = new JsonFactory();

    public JsonRpcHandler(RpcClient upstream, ConcurrentHashMap<String, LongAdder> counters) {
        this.upstream = upstream;
        this.counters = counters;
    }

    @Override
    public void handle(RoutingContext ctx) {
        Buffer body = ctx.body().buffer();
        if (body == null || body.length() == 0) {
            badRequest(ctx, "-32600", "invalid request: empty body");
            return;
        }

        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");

        try {
            countMethods(body.getBytes());
        } catch (IOException e) {
            badRequest(ctx, "-32700", "parse error");
            return;
        }

        upstream.forward(body).onSuccess(resp -> {
            ctx.response().setStatusCode(200).end(resp);
        }).onFailure(err -> {
            if (err instanceof RpcClient.UpstreamException ue) {
                ctx.response().setStatusCode(mapGatewayStatus(ue.status())).end(ue.body() != null ? ue.body() : "");
            } else {
                var errObj = jsonRpcError(null, -32000, "upstream error: " + err.getMessage());
                ctx.response().setStatusCode(504).end(errObj.encode());
            }
        });
    }

    private void countMethods(byte[] bytes) throws IOException {
        try (JsonParser p = factory.createParser(bytes)) {
            JsonToken t = p.nextToken();
            if (t == JsonToken.START_ARRAY) {
                while (p.nextToken() == JsonToken.START_OBJECT) {
                    scanOneObjectForMethod(p);
                }
            } else if (t == JsonToken.START_OBJECT) {
                scanOneObjectForMethod(p);
            }
        }
    }

    private void scanOneObjectForMethod(JsonParser p) throws IOException {
        String method = null;
        int depth = 1;
        while (depth > 0) {
            JsonToken t = p.nextToken();
            if (t == JsonToken.FIELD_NAME) {
                String name = p.getCurrentName();
                if ("method".equals(name)) {
                    p.nextToken();
                    if (p.currentToken().isScalarValue()) {
                        method = p.getValueAsString();
                    }
                }
            } else if (t == JsonToken.START_OBJECT) {
                depth++;
            } else if (t == JsonToken.END_OBJECT) {
                depth--;
            } else if (t == null) break;
        }
        if (method != null && !method.isBlank()) {
            counters.computeIfAbsent(method, k -> new LongAdder()).increment();
        }
    }

    private void badRequest(RoutingContext ctx, String code, String message) {
        var err = jsonRpcError(null, Integer.parseInt(code), message);
        ctx.response().setStatusCode(400).end(err.encode());
    }

    private JsonObject jsonRpcError(Object id, int code, String message) {
        return new JsonObject()
                .put("jsonrpc", "2.0")
                .put("id", id == null ? null : id)
                .put("error", new JsonObject().put("code", code).put("message", message));
    }

    private int mapGatewayStatus(int upstreamStatus) {
        // Map upstream 4xx/5xx to gateway 502 or pass-through 4xx if appropriate.
        if (upstreamStatus >= 500) return 502;
        if (upstreamStatus == 408) return 504;
        return upstreamStatus;
    }
}
