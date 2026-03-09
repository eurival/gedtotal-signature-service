package br.com.arquivototal.gedtotalsignature.infrastructure.http;

import java.util.Map;

public record SignatureDocumentPayload(
    Long arquivoId,
    Long masterDadosIndexacaoId,
    Long tenantRootId,
    Long clienteId,
    Long clientePaiId,
    Long departamentoId,
    Long projetoId,
    Long formularioId,
    String nomeArquivo,
    String hashAtual,
    String downloadUrl,
    Map<String, Object> configuracao
) {
}
