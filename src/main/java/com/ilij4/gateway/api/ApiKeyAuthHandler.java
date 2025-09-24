package com.ilij4.gateway.api;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public record ApiKeyAuthHandler(String expected) implements Handler<RoutingContext> {
    @Override
    public void handle(RoutingContext ctx) {
        if (expected == null || expected.isBlank()) {
            ctx.response().setStatusCode(500).end("Token API key not set");
            return;
        }
        String got = ctx.request().getHeader("X-API-Key");
        if (expected.equals(got)) ctx.next();
        else ctx.response().setStatusCode(401).end("unauthorized");
    }
}