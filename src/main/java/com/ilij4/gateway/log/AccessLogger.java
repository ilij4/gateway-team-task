package com.ilij4.gateway.log;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public class AccessLogger {
    private AccessLogger() {}

    public static Handler<RoutingContext> create() {
        return ctx -> {
            long start = System.nanoTime();
            ctx.addBodyEndHandler(v -> {
                long ms = (System.nanoTime() - start) / 1_000_000;
                var req = ctx.request();
                var res = ctx.response();
                System.out.printf("%s %s %d %dB %dms ua=\"%s\" ip=%s%n",
                        req.method(), req.path(),
                        res.getStatusCode(),
                        res.bytesWritten(),
                        ms,
                        req.getHeader("User-Agent"),
                        req.remoteAddress() != null ? req.remoteAddress().host() : "-");
            });
            ctx.next();
        };
    }
}
