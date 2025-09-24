// com.ilij4.gateway.rpc.RpcRequest.java
package com.ilij4.gateway.rpc;

import io.vertx.core.buffer.Buffer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RpcRequest {
    private final Buffer body;
    private final Map<String, String> headers;
    private final Integer timeoutMs;   // optional override
    private final String correlationId;
    private final int maxRetries;      // 0 = no retry

    private RpcRequest(Buffer body, Map<String, String> headers, Integer timeoutMs, String correlationId, int maxRetries) {
        this.body = body; this.headers = headers; this.timeoutMs = timeoutMs; this.correlationId = correlationId; this.maxRetries = maxRetries;
    }

    public Buffer body() { return body; }
    public Map<String, String> headers() { return Collections.unmodifiableMap(headers); }
    public Integer timeoutMs() { return timeoutMs; }
    public String correlationId() { return correlationId; }
    public int maxRetries() { return maxRetries; }

    public static Builder of(Buffer body) { return new Builder(body); }
    public static final class Builder {
        private final Buffer body;
        private final Map<String,String> headers = new LinkedHashMap<>();
        private Integer timeoutMs;
        private String correlationId;
        private int maxRetries = 1;
        private Builder(Buffer body) { this.body = body; }
        public Builder header(String k, String v) { if (k!=null && v!=null) headers.put(k, v); return this; }
        public Builder headers(Map<String,String> m) { if (m!=null) headers.putAll(m); return this; }
        public Builder timeoutMs(Integer t) { this.timeoutMs = t; return this; }
        public Builder correlationId(String id) { this.correlationId = id; return this; }
        public Builder maxRetries(int r) { this.maxRetries = Math.max(0, r); return this; }
        public RpcRequest build() { return new RpcRequest(body, headers, timeoutMs, correlationId, maxRetries); }
    }
}
