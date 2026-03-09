package br.com.arquivototal.gedtotalsignature.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka.topics")
public record KafkaTopicsProperties(
    String signatureRequest,
    String signatureResult,
    String signatureFailure
) {
}
