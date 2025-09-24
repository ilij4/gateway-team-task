package com.ilij4.gateway;

import com.ilij4.gateway.api.HttpVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
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

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(VertxExtension.class)
@Tag("integration")
class AuthAndPublicTest {

    private static final int PORT = 18081;
    private static final String BASE = "http://localhost:" + PORT;
    private static final String RPC_URL = "https://ethereum-rpc.publicnode.com";

    private static final String JWT_SECRET = "auth-test-secret-12345678901234567890";
    private static final String JWT_ISS = "my-gateway";
    private static final String JWT_AUD = "my-clients";

    private String goodToken;
    private String badSigToken;
    private String wrongAudToken;

    @BeforeAll
    void boot(Vertx vertx, VertxTestContext tc) {
        var cfg = new JsonObject()
                .put("TLS_ENABLED", "false")
                .put("PORT", PORT)
                .put("RPC_URL", RPC_URL)
                .put("RPC_TIMEOUT_MS", 10_000)
                .put("MAX_BODY_BYTES", 10 * 1024 * 1024L)
                .put("JWT_SECRET", JWT_SECRET)
                .put("JWT_ISS", JWT_ISS)
                .put("JWT_AUD", JWT_AUD);

        vertx.deployVerticle(new HttpVerticle(), new DeploymentOptions().setConfig(cfg));

        // Make tokens
        var auth = JWTAuth.create(vertx, new JWTAuthOptions()
                .addPubSecKey(new PubSecKeyOptions().setAlgorithm("HS256").setBuffer(JWT_SECRET)));
        goodToken = auth.generateToken(
                new JsonObject().put("sub", "tester").put("iss", JWT_ISS).put("aud", JWT_AUD),
                new JWTOptions().setAlgorithm("HS256").setExpiresInMinutes(30));

        var other = JWTAuth.create(vertx, new JWTAuthOptions()
                .addPubSecKey(new PubSecKeyOptions().setAlgorithm("HS256").setBuffer("different-secret")));
        badSigToken = other.generateToken(
                new JsonObject().put("sub", "tester").put("iss", JWT_ISS).put("aud", JWT_AUD),
                new JWTOptions().setAlgorithm("HS256").setExpiresInMinutes(30));

        wrongAudToken = auth.generateToken(
                new JsonObject().put("sub", "tester").put("iss", JWT_ISS).put("aud", "not-my-clients"),
                new JWTOptions().setAlgorithm("HS256").setExpiresInMinutes(30));

        tc.completeNow();
    }

    private WebClient client(Vertx vertx) {
        return WebClient.create(vertx, new WebClientOptions().setFollowRedirects(true).setKeepAlive(true));
    }

    @Test
    void health_is_public(Vertx vertx, VertxTestContext tc) {
        client(vertx).getAbs(BASE + "/health").send()
                .onSuccess(resp -> {
                    Assertions.assertEquals(200, resp.statusCode());
                    tc.completeNow();
                })
                .onFailure(tc::failNow);
    }

    @Test
    void rpc_requires_token(Vertx vertx, VertxTestContext tc) {
        String payload = """
      {"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}
      """;
        client(vertx).postAbs(BASE + "/rpc")
                .putHeader("Content-Type","application/json")
                .sendBuffer(Buffer.buffer(payload))
                .onSuccess(resp -> {
                    Assertions.assertEquals(401, resp.statusCode());
                    tc.completeNow();
                })
                .onFailure(tc::failNow);
    }

    @Test
    void metrics_requires_token(Vertx vertx, VertxTestContext tc) {
        client(vertx).getAbs(BASE + "/metrics").send()
                .onSuccess(resp -> {
                    Assertions.assertEquals(401, resp.statusCode());
                    tc.completeNow();
                })
                .onFailure(tc::failNow);
    }

    @Test
    void invalid_signature_token_rejected(Vertx vertx, VertxTestContext tc) {
        client(vertx).getAbs(BASE + "/metrics")
                .putHeader("Authorization","Bearer " + badSigToken)
                .send()
                .onSuccess(resp -> {
                    Assertions.assertEquals(401, resp.statusCode());
                    tc.completeNow();
                })
                .onFailure(tc::failNow);
    }

    @Test
    void wrong_audience_rejected(Vertx vertx, VertxTestContext tc) {
        client(vertx).getAbs(BASE + "/metrics")
                .putHeader("Authorization","Bearer " + wrongAudToken)
                .send()
                .onSuccess(resp -> {
                    Assertions.assertEquals(401, resp.statusCode());
                    tc.completeNow();
                })
                .onFailure(tc::failNow);
    }

    @Test
    void good_token_allows_rpc(Vertx vertx, VertxTestContext tc) {
        String payload = """
      {"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}
      """;
        client(vertx).postAbs(BASE + "/rpc")
                .putHeader("Authorization","Bearer " + goodToken)
                .putHeader("Content-Type","application/json")
                .sendBuffer(Buffer.buffer(payload))
                .onSuccess(resp -> {
                    Assertions.assertEquals(200, resp.statusCode());
                    Assertions.assertTrue(resp.bodyAsJsonObject().getString("result").startsWith("0x"));
                    tc.completeNow();
                })
                .onFailure(tc::failNow);
    }
}
