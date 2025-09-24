package com.ilij4.gateway;

import com.ilij4.gateway.api.HttpVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class Main {
    public static void main(String[] args) {
        var vertx = Vertx.vertx();

        JsonObject cfg = new JsonObject()
                .put("TLS_ENABLED", getEnv("TLS_ENABLED", "false"))
                .put("TLS_P12_PATH", getEnv("TLS_P12_PATH", ""))
                .put("TLS_P12_PASSWORD", getEnv("TLS_P12_PASSWORD", ""))
                .put("PORT", Integer.parseInt(getEnv("PORT", getEnv("TLS_ENABLED", "false").equalsIgnoreCase("true") ? "8443" : "8080")))
                .put("RPC_URL", getEnv("RPC_URL", ""))
                .put("RPC_TIMEOUT_MS", Integer.parseInt(getEnv("RPC_TIMEOUT_MS", "10000")))
                .put("MAX_BODY_BYTES", Long.parseLong(getEnv("MAX_BODY_BYTES", "10485760"))) // 10MB
                .put("JWT_SECRET", getEnv("JWT_SECRET", ""))
                .put("JWT_ISS", getEnv("JWT_ISS", ""))
                .put("JWT_AUD", getEnv("JWT_AUD", ""))
                .put("TOKEN_API_KEY", getEnv("TOKEN_API_KEY", "test-apikey"));

        vertx.deployVerticle(new HttpVerticle(), new DeploymentOptions().setConfig(cfg));
    }

    private static String getEnv(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }
}
