package com.ilij4.gateway.http;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class MetricsHandler implements Handler<RoutingContext> {

    private final ConcurrentHashMap<String, LongAdder> counters;

    public MetricsHandler(ConcurrentHashMap<String, LongAdder> counters) {
        this.counters = counters;
    }

    @Override
    public void handle(RoutingContext ctx) {
        JsonObject root = new JsonObject();
        JsonObject methods = new JsonObject();
        for (Map.Entry<String, LongAdder> e : counters.entrySet()) {
            methods.put(e.getKey(), e.getValue().sum());
        }
        root.put("methods", methods);
        ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(root.encode());
    }

}
