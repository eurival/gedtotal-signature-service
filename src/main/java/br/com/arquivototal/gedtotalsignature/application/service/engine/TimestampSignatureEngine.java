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
public class TimestampSignatureEngine implements SignatureEngine {

    private final SignatureProperties signatureProperties;

    @Override
    public SignatureStepType supports() {
        return SignatureStepType.CARIMBO_TEMPO;
    }

    @Override
    public SignatureStepOutput apply(byte[] documentBytes, SignatureDocumentPayload payload, String traceId) {
        SignatureProperties.Timestamp config = signatureProperties.timestamp();
        ZonedDateTime now = ZonedDateTime.now();
        byte[] stampedBytes = PdfAppendSupport.appendStamp(
            documentBytes,
            "Carimbo de tempo",
            "authority=" + (config != null && config.authorityName() != null ? config.authorityName() : "GedTotal Time"),
            "tsa=" + (config != null ? config.tsaUrl() : null) + " at=" + now
        );
        String hash = HashUtils.sha256Hex(stampedBytes);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("engine", "timestamp-append");
        metadata.put("enabled", config != null && config.enabled());
        metadata.put("tsaUrl", config != null ? config.tsaUrl() : null);
        metadata.put("authorityName", config != null ? config.authorityName() : null);
        metadata.put("timestamp", now.toString());
        metadata.put("outputHash", hash);
        return new SignatureStepOutput(stampedBytes, "memory://timestamp/" + hash, metadata);
    }
}
