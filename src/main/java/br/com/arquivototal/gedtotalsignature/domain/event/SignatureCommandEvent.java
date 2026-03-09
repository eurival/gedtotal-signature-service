package br.com.arquivototal.gedtotalsignature.domain.event;

import br.com.arquivototal.gedtotalsignature.domain.enumeration.SignatureStepType;
import java.util.List;

public record SignatureCommandEvent(
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
    List<SignatureStepType> etapas,
    String payloadUrl,
    String traceId
) {
}
