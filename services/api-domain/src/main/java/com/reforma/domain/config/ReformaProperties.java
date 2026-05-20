package com.reforma.domain.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reforma")
public record ReformaProperties(Jwt jwt, Ml ml, String frontendUrl) {

    public record Jwt(String secret, int expirationHours) {}

    public record Ml(String baseUrl, String secret) {}
}
