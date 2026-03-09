package br.com.arquivototal.gedtotalsignature.infrastructure.kafka;

import br.com.arquivototal.gedtotalsignature.config.KafkaTopicsProperties;
import br.com.arquivototal.gedtotalsignature.domain.event.SignatureFailureEvent;
import br.com.arquivototal.gedtotalsignature.domain.event.SignatureResultEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SignatureEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicsProperties kafkaTopicsProperties;

    public void publishResult(SignatureResultEvent event) {
        log.info("Publicando resultado de assinatura jobId={} topico={}", event.jobId(), kafkaTopicsProperties.signatureResult());
        kafkaTemplate.send(kafkaTopicsProperties.signatureResult(), event.jobId(), event);
    }

    public void publishFailure(SignatureFailureEvent event) {
        log.info("Publicando falha de assinatura jobId={} topico={}", event.jobId(), kafkaTopicsProperties.signatureFailure());
        kafkaTemplate.send(kafkaTopicsProperties.signatureFailure(), event.jobId(), event);
    }
}
