package com.ilij4.gateway.http;

import com.ilij4.gateway.config.ConfigKeys;
import com.ilij4.gateway.log.AccessLogger;
import com.ilij4.gateway.rpc.RpcClient;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class Routes {
    private Routes() {}
    private static final Logger log = LoggerFactory.getLogger(Routes.class);

    public static Router create(Vertx vertx, JsonObject cfg) {
        Router router = Router.router(vertx);

        long maxBody = cfg.getLong(ConfigKeys.MAX_BODY_BYTES, 10 * 1024 * 1024L);
        router.route().handler(BodyHandler.create().setBodyLimit(maxBody));

        router.route().handler(AccessLogger.create());

        router.get("/health").handler(new HealthHandler());

        var counters = new ConcurrentHashMap<String, LongAdder>();
        router.get("/metrics").handler(new MetricsHandler(counters));

        log.info(cfg.getString(ConfigKeys.RPC_URL));
        log.info(cfg.getString(ConfigKeys.RPC_TIMEOUT_MS));

        var upstream = new RpcClient(vertx, cfg.getString(ConfigKeys.RPC_URL),
                cfg.getInteger(ConfigKeys.RPC_TIMEOUT_MS, 10_000));

        router.post("/rpc").handler(new JsonRpcHandler(upstream, counters));

        return router;
    }
}
