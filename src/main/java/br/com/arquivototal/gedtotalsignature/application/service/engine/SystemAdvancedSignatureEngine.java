package br.com.arquivototal.gedtotalsignature.application.service.engine;

import br.com.arquivototal.gedtotalsignature.application.service.support.HashUtils;
import br.com.arquivototal.gedtotalsignature.config.SignatureProperties;
import br.com.arquivototal.gedtotalsignature.domain.enumeration.SignatureStepType;
import br.com.arquivototal.gedtotalsignature.infrastructure.http.SignatureDocumentPayload;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SystemAdvancedSignatureEngine implements SignatureEngine {

    private final SignatureProperties signatureProperties;

    @Override
    public SignatureStepType supports() {
        return SignatureStepType.ASSINATURA_AVANCADA_SISTEMA;
    }

    @Override
    public SignatureStepOutput apply(byte[] documentBytes, SignatureDocumentPayload payload, String traceId) {
        SignatureProperties.AdvancedSystem config = signatureProperties.advancedSystem();
        String signerName = config != null && config.signerName() != null ? config.signerName() : "GedTotal Advanced Signature";
        ZonedDateTime signedAt = ZonedDateTime.now();
        SignatureDocumentPayload.VisualConfig visual = payload.visual();
        byte[] signedBytes = PdfAppendSupport.appendStamp(
            documentBytes,
            new PdfAppendSupport.SignatureVisualSpec(
                supports().name(),
                signerName,
                payload.nomeArquivo(),
                payload.hashAtual(),
                payload.validacao() != null ? payload.validacao().codigoValidacao() : null,
                payload.validacao() != null ? payload.validacao().urlValidacao() : null,
                visual != null ? visual.modoCarimbo() : null,
                visual != null ? visual.posicaoCarimbo() : null,
                visual != null ? visual.templateVisual() : null,
                visual == null || visual.habilitarCodigoValidacao(),
                visual == null || visual.habilitarQrCode(),
                visual == null || visual.mostrarHashDocumento(),
                visual == null || visual.mostrarDadosAssinatura(),
                visual != null && visual.gerarPaginaCertificado(),
                signedAt
            )
        );
        String hash = HashUtils.sha256Hex(signedBytes);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("engine", "system-advanced");
        metadata.put("signerName", signerName);
        metadata.put("reason", config != null ? config.reason() : null);
        metadata.put("location", config != null ? config.location() : null);
        metadata.put("timestamp", signedAt.toString());
        metadata.put("validationCode", payload.validacao() != null ? payload.validacao().codigoValidacao() : null);
        metadata.put("validationUrl", payload.validacao() != null ? payload.validacao().urlValidacao() : null);
        metadata.put("visualMode", visual != null ? visual.modoCarimbo() : null);
        metadata.put("visualTemplate", visual != null ? visual.templateVisual() : null);
        metadata.put("outputHash", hash);
        return new SignatureStepOutput(signedBytes, "memory://system-advanced/" + hash, metadata);
    }
}
