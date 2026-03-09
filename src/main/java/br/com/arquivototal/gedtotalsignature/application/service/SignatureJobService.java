package br.com.arquivototal.gedtotalsignature.application.service;

import br.com.arquivototal.gedtotalsignature.application.service.engine.SignatureEngine;
import br.com.arquivototal.gedtotalsignature.application.service.engine.SignatureStepOutput;
import br.com.arquivototal.gedtotalsignature.application.service.support.HashUtils;
import br.com.arquivototal.gedtotalsignature.domain.enumeration.ProcessingStatus;
import br.com.arquivototal.gedtotalsignature.domain.enumeration.SignatureStepType;
import br.com.arquivototal.gedtotalsignature.domain.event.SignatureCommandEvent;
import br.com.arquivototal.gedtotalsignature.domain.event.SignatureFailureEvent;
import br.com.arquivototal.gedtotalsignature.domain.event.SignatureResultEvent;
import br.com.arquivototal.gedtotalsignature.infrastructure.http.CustodiaArtifactResponse;
import br.com.arquivototal.gedtotalsignature.infrastructure.http.GedtotalApiClient;
import br.com.arquivototal.gedtotalsignature.infrastructure.http.SignatureDocumentPayload;
import br.com.arquivototal.gedtotalsignature.infrastructure.kafka.SignatureEventPublisher;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final List<SignatureEngine> signatureEngines;

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
            String hashOriginal = HashUtils.sha256Hex(content);
            byte[] currentDocument = content;

            log.info(
                "Payload obtido jobId={} arquivoId={} formularioId={} bytes={} nomeArquivo={}",
                event.jobId(),
                payload.arquivoId(),
                payload.formularioId(),
                content.length,
                payload.nomeArquivo()
            );

            for (SignatureStepType etapa : event.etapas()) {
                SignatureEngine engine = resolveEngine(etapa);
                SignatureStepOutput output = engine.apply(currentDocument, payload, event.traceId());
                currentDocument = output.documentBytes();
                String hashFinal = HashUtils.sha256Hex(currentDocument);
                CustodiaArtifactResponse artifact = gedtotalApiClient.uploadArtifact(
                    payload.arquivoId(),
                    currentDocument,
                    payload.nomeArquivo(),
                    etapa.name()
                );
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("message", "Etapa processada pelo worker de assinatura");
                metadata.put("nomeArquivo", payload.nomeArquivo());
                metadata.put("bytesEntrada", content.length);
                metadata.put("bytesSaida", currentDocument.length);
                metadata.put("hashPayload", payload.hashAtual());
                metadata.put("artifactArquivoId", artifact.artifactArquivoId());
                metadata.put("artifactNomeArquivo", artifact.nomeArquivo());
                metadata.putAll(output.metadata());

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
                        ProcessingStatus.CONCLUIDO,
                        hashOriginal,
                        hashFinal,
                        artifact.artifactRef(),
                        metadata,
                        event.traceId()
                    )
                );
            }
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

    private SignatureEngine resolveEngine(SignatureStepType etapa) {
        return signatureEngines
            .stream()
            .filter(engine -> engine.supports() == etapa)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Nenhuma engine configurada para a etapa " + etapa));
    }
}
