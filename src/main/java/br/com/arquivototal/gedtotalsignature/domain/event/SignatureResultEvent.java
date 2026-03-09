package br.com.arquivototal.gedtotalsignature.domain.event;

import br.com.arquivototal.gedtotalsignature.domain.enumeration.ProcessingStatus;
import br.com.arquivototal.gedtotalsignature.domain.enumeration.SignatureStepType;
import java.util.Map;

public record SignatureResultEvent(
    String jobId,
    Long arquivoId,
    Long custodiaDocumentoId,
    Long masterDadosIndexacaoId,
    Long tenantRootId,
    Long clienteId,
    Long clientePaiId,
    Long departamentoId,
    Long projetoId,
    Long formularioId,
    SignatureStepType etapa,
    ProcessingStatus status,
    String hashOriginal,
    String hashFinal,
    String artefatoRef,
    Map<String, Object> metadata,
    String traceId
) {
}
