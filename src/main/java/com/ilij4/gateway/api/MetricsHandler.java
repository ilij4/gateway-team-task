package com.ilij4.gateway.api;

import com.ilij4.gateway.services.MetricsService;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class MetricsHandler implements Handler<RoutingContext> {

    private final MetricsService metrics;

    public MetricsHandler(MetricsService metrics) {
        this.metrics = metrics;
    }

    @Override
    public void handle(RoutingContext ctx) {
        JsonObject methods = metrics.asJson();

        ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(methods.encode());
    }

}
