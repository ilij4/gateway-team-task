package com.ilij4.gateway.services;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import io.vertx.core.json.JsonObject;

public class MetricsService {
    private final ConcurrentHashMap<String, LongAdder> perMethod = new ConcurrentHashMap<>();

    public void inc(String method) {
        if (method == null || method.isBlank()) return;
        perMethod.computeIfAbsent(method, k -> new LongAdder()).increment();
    }

    public JsonObject asJson() {
        var methods = new JsonObject();
        for (Map.Entry<String, LongAdder> e : perMethod.entrySet()) {
            methods.put(e.getKey(), e.getValue().sum());
        }
        return new JsonObject().put("methods", methods);
    }

    public Map<String, LongAdder> raw() { return perMethod; }
}
