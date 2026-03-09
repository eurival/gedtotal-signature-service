package br.com.arquivototal.gedtotalsignature.application.service.engine;

import br.com.arquivototal.gedtotalsignature.application.service.support.HashUtils;
import br.com.arquivototal.gedtotalsignature.config.SignatureProperties;
import br.com.arquivototal.gedtotalsignature.domain.enumeration.SignatureStepType;
import br.com.arquivototal.gedtotalsignature.infrastructure.http.SignatureDocumentPayload;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.tsp.TSPAlgorithms;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampToken;
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
        if (config == null || !config.enabled()) {
            throw new IllegalStateException("Carimbo do tempo habilitado na esteira, mas app.signature.timestamp.enabled=false");
        }
        if (config.tsaUrl() == null || config.tsaUrl().isBlank()) {
            throw new IllegalStateException("app.signature.timestamp.tsa-url nao configurado");
        }

        byte[] stampedBytes = signTimestamp(documentBytes, config, traceId);
        String hash = HashUtils.sha256Hex(stampedBytes);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("engine", "rfc3161-doc-timestamp");
        metadata.put("enabled", true);
        metadata.put("tsaUrl", config.tsaUrl());
        metadata.put("authorityName", config.authorityName());
        metadata.put("outputHash", hash);
        return new SignatureStepOutput(stampedBytes, "memory://timestamp/" + hash, metadata);
    }

    private byte[] signTimestamp(byte[] documentBytes, SignatureProperties.Timestamp config, String traceId) {
        try (PDDocument document = Loader.loadPDF(documentBytes); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDSignature signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(PDSignature.SUBFILTER_ETSI_RFC3161);
            signature.setName(config.authorityName() != null ? config.authorityName() : "GedTotal TSA");
            signature.setReason("Carimbo do tempo RFC3161");
            signature.setLocation("GedTotal");
            signature.setSignDate(java.util.Calendar.getInstance());

            SignatureOptions options = new SignatureOptions();
            options.setPreferredSignatureSize(SignatureOptions.DEFAULT_SIGNATURE_SIZE * 4);

            SignatureInterface signatureInterface = content -> {
                try {
                    return requestTimestampToken(content, config.tsaUrl());
                } catch (Exception ex) {
                    throw new IOException("Falha ao obter token RFC3161 da TSA", ex);
                }
            };

            document.addSignature(signature, signatureInterface, options);
            document.getDocumentInformation().setCustomMetadataValue("gedtotal-trace-id", traceId);
            document.saveIncremental(output);
            options.close();
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao aplicar carimbo de tempo RFC3161", ex);
        }
    }

    private byte[] requestTimestampToken(InputStream content, String tsaUrl) throws Exception {
        byte[] bytes = content.readAllBytes();
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);

        TimeStampRequestGenerator generator = new TimeStampRequestGenerator();
        generator.setCertReq(true);
        generator.setReqPolicy((ASN1ObjectIdentifier) null);
        TimeStampRequest request = generator.generate(TSPAlgorithms.SHA256, digest, new java.math.BigInteger(64, new SecureRandom()));

        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(20)).build();
        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(tsaUrl))
            .timeout(java.time.Duration.ofSeconds(60))
            .header("Content-Type", "application/timestamp-query")
            .header("Accept", "application/timestamp-reply")
            .POST(HttpRequest.BodyPublishers.ofByteArray(request.getEncoded()))
            .build();

        HttpResponse<byte[]> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("TSA respondeu com status HTTP " + response.statusCode());
        }

        TimeStampResponse tsResponse = new TimeStampResponse(response.body());
        tsResponse.validate(request);
        TimeStampToken token = tsResponse.getTimeStampToken();
        if (token == null) {
            throw new IllegalStateException("Resposta da TSA sem token RFC3161");
        }
        return token.getEncoded();
    }
}
