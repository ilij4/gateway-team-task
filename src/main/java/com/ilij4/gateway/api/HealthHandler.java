package com.ilij4.gateway.api;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public class HealthHandler implements Handler<RoutingContext> {
    public void handle(RoutingContext ctx) {
        ctx.response().putHeader("Content-Type", "text/plain").end("ok");
    }
}
