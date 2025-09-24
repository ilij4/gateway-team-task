package com.ilij4.gateway.config;

public class ConfigKeys {
    private ConfigKeys() {}
    public static final String TLS_ENABLED = "TLS_ENABLED";
    public static final String TLS_P12_PATH = "TLS_P12_PATH";
    public static final String TLS_P12_PASSWORD = "TLS_P12_PASSWORD";
    public static final String PORT = "PORT";
    public static final String RPC_URL = "RPC_URL";
    public static final String RPC_TIMEOUT_MS = "RPC_TIMEOUT_MS";
    public static final String MAX_BODY_BYTES = "MAX_BODY_BYTES";
    public static final String JWT_SECRET = "JWT_SECRET";
    public static final String JWT_ISS    = "JWT_ISS";
    public static final String JWT_AUD    = "JWT_AUD";
    public static final String TOKEN_API_KEY = "TOKEN_API_KEY";
}
