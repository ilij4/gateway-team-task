package com.ilij4.gateway.api;

import com.ilij4.gateway.config.ConfigKeys;
import com.ilij4.gateway.log.AccessLogger;
import com.ilij4.gateway.rpc.RpcClient;
import com.ilij4.gateway.services.MetricsService;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;

public class Routes {
    public static Router create(Vertx vertx, JsonObject cfg) {
        var jwtSecret = cfg.getString(ConfigKeys.JWT_SECRET, null);
        var jwtIss    = cfg.getString(ConfigKeys.JWT_ISS, null);
        var jwtAud    = cfg.getString(ConfigKeys.JWT_AUD, null);
        var tokenApiKey = cfg.getString(ConfigKeys.TOKEN_API_KEY, "");

        Router router = Router.router(vertx);

        long maxBody = cfg.getLong(ConfigKeys.MAX_BODY_BYTES, 10 * 1024 * 1024L);
        router.route().handler(BodyHandler.create().setBodyLimit(maxBody));

        router.route().handler(AccessLogger.create());
         router.route().handler(CorsHandler.create()
           .addOrigin("*") // or restrict
           .allowedHeader("Content-Type")
           .allowedHeader("Authorization"));

        router.get("/health").handler(new HealthHandler());

        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET not configured;");
        }

        var jwt = JWTAuth.create(vertx, new JWTAuthOptions()
                .addPubSecKey(new PubSecKeyOptions()
                        .setAlgorithm("HS256")
                        .setBuffer(jwtSecret)));

        router.post("/auth/token")
                .handler(new ApiKeyAuthHandler(tokenApiKey))
                .handler(ctx -> {
                    var sub = ctx.body().asJsonObject() != null ? ctx.body().asJsonObject().getString("sub", "client") : "client";

                    JsonObject claims = new JsonObject().put("sub", sub);
                    if (jwtIss != null) claims.put("iss", jwtIss);
                    if (jwtAud != null) claims.put("aud", jwtAud);

                    String token = jwt.generateToken(claims,
                            new JWTOptions().setAlgorithm("HS256").setExpiresInMinutes(60));
                    ctx.response().putHeader("Content-Type", "application/json")
                            .end(new JsonObject().put("token", token).encode());
                });

        var metrics = new MetricsService();

        router.get("/metrics")
                .handler(JWTAuthHandler.create(jwt))
                .handler(ctx -> {
                    if (!claimsOk(ctx, jwtIss, jwtAud)) return; // sends 401 if invalid
                    ctx.next();
                }).handler(new MetricsHandler(metrics));

        var rpcClient = new RpcClient(vertx, cfg.getString(ConfigKeys.RPC_URL),
                cfg.getInteger(ConfigKeys.RPC_TIMEOUT_MS, 10_000));

        router.post("/rpc")
                .handler(JWTAuthHandler.create(jwt))
                .handler(ctx -> {
                    if (!claimsOk(ctx, jwtIss, jwtAud)) return;
                    ctx.next();
                }).handler(new JsonRpcHandler(rpcClient, metrics));

        return router;
    }

    private static boolean claimsOk(RoutingContext ctx, String requiredIss, String requiredAud) {
        if (ctx.user() == null) {
            ctx.response().setStatusCode(401).end("unauthorized");
            return false;
        }

        JsonObject claims = ctx.user().principal();

        if (requiredIss != null && !requiredIss.isBlank()) {
            String iss = claims.getString("iss");
            if (!requiredIss.equals(iss)) {
                ctx.response().setStatusCode(401).end("invalid issuer");
                return false;
            }
        }

        if (requiredAud != null && !requiredAud.isBlank()) {
            Object audVal = claims.getValue("aud");
            boolean ok = false;
            if (audVal instanceof String s) {
                ok = requiredAud.equals(s);
            } else if (audVal instanceof JsonArray arr) {
                ok = arr.contains(requiredAud);
            }
            if (!ok) {
                ctx.response().setStatusCode(401).end("invalid audience");
                return false;
            }
        }

        return true;
    }
}
