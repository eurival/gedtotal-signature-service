package br.com.arquivototal.gedtotalsignature.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.signature")
public record SignatureProperties(
    AdvancedSystem advancedSystem,
    QualifiedIcpBrasil qualifiedIcpBrasil,
    Timestamp timestamp
) {
    public record AdvancedSystem(String signerName, String reason, String location) {}

    public record QualifiedIcpBrasil(
        boolean enabled,
        String keyStorePath,
        String keyStorePassword,
        String keyAlias,
        String keyPassword,
        String signerName,
        String reason,
        String location
    ) {}

    public record Timestamp(boolean enabled, String tsaUrl, String authorityName) {}
}
