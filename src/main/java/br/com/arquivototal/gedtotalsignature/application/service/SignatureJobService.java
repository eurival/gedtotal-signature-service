package br.com.arquivototal.gedtotalsignature.application.service;

import br.com.arquivototal.gedtotalsignature.domain.enumeration.ProcessingStatus;
import br.com.arquivototal.gedtotalsignature.domain.event.SignatureCommandEvent;
import br.com.arquivototal.gedtotalsignature.domain.event.SignatureFailureEvent;
import br.com.arquivototal.gedtotalsignature.domain.event.SignatureResultEvent;
import br.com.arquivototal.gedtotalsignature.infrastructure.http.GedtotalApiClient;
import br.com.arquivototal.gedtotalsignature.infrastructure.http.SignatureDocumentPayload;
import br.com.arquivototal.gedtotalsignature.infrastructure.kafka.SignatureEventPublisher;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignatureJobService {

    private final GedtotalApiClient gedtotalApiClient;
    private final SignatureEventPublisher signatureEventPublisher;

    public void handle(SignatureCommandEvent event) {
        log.info(
            "Recebido comando de assinatura jobId={} arquivoId={} custodiaDocumentoId={} etapas={}",
            event.jobId(),
            event.arquivoId(),
            event.custodiaDocumentoId(),
            event.etapas()
        );

        try {
            SignatureDocumentPayload payload = gedtotalApiClient.fetchPayload(event.payloadUrl());
            byte[] content = gedtotalApiClient.fetchDocumentContent(payload.downloadUrl());

            log.info(
                "Payload obtido jobId={} arquivoId={} formularioId={} bytes={} nomeArquivo={}",
                event.jobId(),
                payload.arquivoId(),
                payload.formularioId(),
                content.length,
                payload.nomeArquivo()
            );

            event.etapas().forEach(etapa ->
                signatureEventPublisher.publishResult(
                    new SignatureResultEvent(
                        event.jobId(),
                        event.arquivoId(),
                        event.custodiaDocumentoId(),
                        event.masterDadosIndexacaoId(),
                        event.tenantRootId(),
                        event.clienteId(),
                        event.clientePaiId(),
                        event.departamentoId(),
                        event.projetoId(),
                        event.formularioId(),
                        etapa,
                        ProcessingStatus.RECEBIDO,
                        payload.hashAtual(),
                        payload.hashAtual(),
                        null,
                        Map.of("message", "Etapa recebida e pronta para implementacao", "nomeArquivo", payload.nomeArquivo()),
                        event.traceId()
                    )
                )
            );
        } catch (Exception ex) {
            log.error(
                "Falha ao processar comando de assinatura jobId={} arquivoId={} erro={}",
                event.jobId(),
                event.arquivoId(),
                ex.getMessage(),
                ex
            );
            signatureEventPublisher.publishFailure(
                new SignatureFailureEvent(
                    event.jobId(),
                    event.arquivoId(),
                    event.custodiaDocumentoId(),
                    event.masterDadosIndexacaoId(),
                    event.tenantRootId(),
                    event.clienteId(),
                    event.clientePaiId(),
                    event.departamentoId(),
                    event.projetoId(),
                    event.formularioId(),
                    event.etapas().isEmpty() ? null : event.etapas().getFirst(),
                    "SIGNATURE_JOB_ERROR",
                    ex.getMessage(),
                    event.traceId()
                )
            );
        }
    }
}
