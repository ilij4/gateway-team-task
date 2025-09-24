package com.ilij4.gateway.api;

import com.ilij4.gateway.config.ConfigKeys;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.PfxOptions;

public class HttpVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> startPromise) {
        final var cfg = config();

        boolean tls = "true".equalsIgnoreCase(cfg.getString(ConfigKeys.TLS_ENABLED, "false"));
        int port = cfg.getInteger(ConfigKeys.PORT, tls ? 8443 : 8080);

        HttpServerOptions options = new HttpServerOptions()
                .setHost("0.0.0.0")
                .setPort(port);

        if (tls) {
            String p12 = cfg.getString(ConfigKeys.TLS_P12_PATH);
            String pwd = cfg.getString(ConfigKeys.TLS_P12_PASSWORD);
            options.setSsl(true)
                    .setKeyCertOptions(new PfxOptions().setPath(p12).setPassword(pwd));
        }

        var router = Routes.create(vertx, cfg);

        vertx.createHttpServer(options)
                .requestHandler(router)
                .listen();
    }

}
