package br.com.arquivototal.gedtotalsignature.infrastructure.http;

public record SignatureDocumentPayload(
    Long arquivoId,
    Long sourceArquivoId,
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
    ValidationInfo validacao,
    VisualConfig visual,
    java.util.Map<String, Object> configuracao
) {
    public record ValidationInfo(String codigoValidacao, String urlValidacao) {}

    public record VisualConfig(
        boolean ativo,
        boolean gerarPaginaCertificado,
        boolean habilitarCodigoValidacao,
        boolean habilitarQrCode,
        boolean mostrarHashDocumento,
        boolean mostrarDadosAssinatura,
        String modoCarimbo,
        String posicaoCarimbo,
        String templateVisual
    ) {}
}
