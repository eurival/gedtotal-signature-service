package br.com.arquivototal.gedtotalsignature.domain.event;

import br.com.arquivototal.gedtotalsignature.domain.enumeration.SignatureStepType;

public record SignatureFailureEvent(
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
    String errorCode,
    String errorMessage,
    String traceId
) {
}
