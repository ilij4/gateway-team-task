package com.ilij4.gateway;

import com.ilij4.gateway.api.HttpVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;
import java.util.stream.Collectors;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(VertxExtension.class)
@Tag("integration")
public class GatewayTest {
    private static final boolean TLS = false;
    private static final int PORT = 18080;
    private static final String RPC_URL = "https://ethereum-rpc.publicnode.com";
    private static final String GATEWAY_BASE_URL = "http://localhost:" + PORT;

    // ---- JWT test config ----
    private static final String JWT_SECRET = "test-secret-please-change";
    private static final String JWT_ISS = "my-gateway";
    private static final String JWT_AUD = "my-clients";

    private String bearerToken; // set in @BeforeAll

    @BeforeAll
    void deployAll(Vertx vertx, VertxTestContext tc) {
        // App config with JWT enabled
        var cfg = new JsonObject()
                .put("TLS_ENABLED", String.valueOf(TLS))
                .put("PORT", PORT)
                .put("RPC_URL", RPC_URL)
                .put("RPC_TIMEOUT_MS", 10000)
                .put("MAX_BODY_BYTES", 10 * 1024 * 1024L)
                // JWT config (HS256)
                .put("JWT_SECRET", JWT_SECRET)
                .put("JWT_ISS", JWT_ISS)   // optional in your code; included here
                .put("JWT_AUD", JWT_AUD);  // optional in your code; included here

        vertx.deployVerticle(new HttpVerticle(), new DeploymentOptions().setConfig(cfg));

        // Create a matching JWT for test calls
        var jwt = JWTAuth.create(vertx, new JWTAuthOptions()
                .addPubSecKey(new PubSecKeyOptions()
                        .setAlgorithm("HS256")
                        .setBuffer(JWT_SECRET)));
        bearerToken = jwt.generateToken(
                new JsonObject()
                        .put("sub", "integration-test")
                        .put("iss", JWT_ISS)
                        .put("aud", JWT_AUD),
                new JWTOptions().setAlgorithm("HS256").setExpiresInMinutes(30)
        );

        tc.completeNow();
    }

    private WebClient client(Vertx vertx) {
        return WebClient.create(vertx, new WebClientOptions()
                .setFollowRedirects(true)
                .setKeepAlive(true));
    }

    private String authHeader() {
        return "Bearer " + bearerToken;
    }

    @Test
    void ethBlockNumber_via_local_gateway_if_configured(Vertx vertx, VertxTestContext tc) {
        WebClient client = client(vertx);

        String payload = """
          {"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}
          """;

        client.postAbs(GATEWAY_BASE_URL + "/rpc")
                .putHeader("Authorization", authHeader())          // <-- JWT
                .putHeader("Content-Type", "application/json")
                .sendBuffer(Buffer.buffer(payload))
                .onSuccess(resp -> {
                    try {
                        Assertions.assertEquals(200, resp.statusCode(), "HTTP status");
                        String hex = resp.bodyAsJsonObject().getString("result");
                        Assertions.assertNotNull(hex, "result present");
                        Assertions.assertTrue(hex.startsWith("0x"), "result is hex");
                        tc.completeNow();
                    } catch (Throwable t) {
                        tc.failNow(t);
                    }
                })
                .onFailure(tc::failNow);
    }

    @Test
    void batch_request_and_metrics(Vertx vertx, VertxTestContext tc) {
        WebClient client = client(vertx);
        String batch = """
          [
            {"jsonrpc":"2.0","method":"eth_getBlockByNumber","params":[],"id":1},
            {"jsonrpc":"2.0","method":"eth_chainId","params":[],"id":2}
          ]
          """;

        client.postAbs(GATEWAY_BASE_URL + "/rpc")
                .putHeader("Authorization", authHeader())          // <-- JWT
                .putHeader("Content-Type", "application/json")
                .sendBuffer(Buffer.buffer(batch))
                .compose(resp -> {
                    try {
                        Assertions.assertEquals(200, resp.statusCode(), "HTTP status");
                        JsonArray arr = resp.bodyAsJsonArray();

                        Map<Integer, JsonObject> byId = arr.stream()
                                .map(o -> (JsonObject) o)
                                .collect(Collectors.toMap(o -> o.getInteger("id"), o -> o));

                        JsonObject r1 = byId.get(1);
                        Assertions.assertEquals("2.0", r1.getString("jsonrpc"));
                        JsonObject err = r1.getJsonObject("error");
                        Assertions.assertNotNull(err, "error present");
                        Assertions.assertEquals(-32602, err.getInteger("code"), "invalid params");

                        JsonObject r2 = byId.get(2);
                        String chainId = r2.getString("result");
                        Assertions.assertNotNull(chainId, "chainId present");
                        Assertions.assertTrue(chainId.startsWith("0x"), "chainId is hex");

                    } catch (Throwable t) {
                        return io.vertx.core.Future.failedFuture(t);
                    }

                    // then call /metrics (also protected by JWT)
                    return client.getAbs(GATEWAY_BASE_URL + "/metrics")
                            .putHeader("Authorization", authHeader()) // <-- JWT
                            .send();
                })
                .onSuccess(metricsResp -> {
                    try {
                        Assertions.assertEquals(200, metricsResp.statusCode());
                        JsonObject root = metricsResp.bodyAsJsonObject();
                        JsonObject methods = root.getJsonObject("methods", new JsonObject());

                        Number nGetBlock = (Number) methods.getValue("eth_getBlockByNumber");
                        Number nChainId  = (Number) methods.getValue("eth_chainId");

                        Assertions.assertNotNull(nGetBlock, "methods.eth_getBlockByNumber missing");
                        Assertions.assertNotNull(nChainId,  "methods.eth_chainId missing");
                        Assertions.assertTrue(nGetBlock.longValue() >= 1L, "eth_getBlockByNumber counter");
                        Assertions.assertTrue(nChainId.longValue()  >= 1L, "eth_chainId counter");
                        tc.completeNow();
                    } catch (Throwable t) {
                        tc.failNow(t);
                    }
                })
                .onFailure(tc::failNow);
    }
}
