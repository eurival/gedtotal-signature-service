package br.com.arquivototal.gedtotalsignature.infrastructure.kafka;

import br.com.arquivototal.gedtotalsignature.application.service.SignatureJobService;
import br.com.arquivototal.gedtotalsignature.domain.event.SignatureCommandEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SignatureCommandListener {

    private final ObjectMapper objectMapper;
    private final SignatureJobService signatureJobService;

    @KafkaListener(topics = "${app.kafka.topics.signature-request}", groupId = "${spring.application.name}")
    public void onMessage(String payload) throws Exception {
        SignatureCommandEvent event = objectMapper.readValue(payload, SignatureCommandEvent.class);
        log.info("Mensagem de assinatura recebida jobId={} arquivoId={}", event.jobId(), event.arquivoId());
        signatureJobService.handle(event);
    }
}
