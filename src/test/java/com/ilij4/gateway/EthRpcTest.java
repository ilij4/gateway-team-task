package com.ilij4.gateway;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
@Tag("integration")
public class EthRpcTest {
    private static String env() {
        String v = System.getenv("UPSTREAM_URL");
        return (v == null || v.isBlank()) ? "https://ethereum-rpc.publicnode.com" : v;
    }

    private static final String RPC_URL =env();

    private WebClient client(Vertx vertx) {
        return WebClient.create(vertx, new WebClientOptions()
                .setFollowRedirects(true).setKeepAlive(true));
    }

    @Test
    void ethBlockNumber_direct_upstream(Vertx vertx, VertxTestContext tc) {
        WebClient client = client(vertx);

        String payload = """
      {"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":83}
      """;

        client.postAbs(RPC_URL)
                .putHeader("Content-Type", "application/json")
                .sendBuffer(Buffer.buffer(payload))
                .onSuccess(resp -> {
                    try {
                        Assertions.assertEquals(200, resp.statusCode(), "HTTP status");
                        String body = resp.bodyAsString();
                        Assertions.assertTrue(body.contains("\"jsonrpc\":\"2.0\""), "jsonrpc version");
                        Assertions.assertTrue(body.contains("\"id\":83"), "id echo");
                        String hex = resp.bodyAsJsonObject().getString("result");
                        Assertions.assertNotNull(hex, "result present");
                        Assertions.assertTrue(hex.startsWith("0x"), "result is hex");
                        long height = Long.parseLong(hex.substring(2), 16);
                        Assertions.assertTrue(height > 0, "block height > 0");
                        tc.completeNow();
                    } catch (Throwable t) {
                        tc.failNow(t);
                    }
                })
                .onFailure(tc::failNow);
    }
}
