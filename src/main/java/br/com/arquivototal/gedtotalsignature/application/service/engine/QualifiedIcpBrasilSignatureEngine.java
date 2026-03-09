package br.com.arquivototal.gedtotalsignature.application.service.engine;

import br.com.arquivototal.gedtotalsignature.application.service.support.HashUtils;
import br.com.arquivototal.gedtotalsignature.config.SignatureProperties;
import br.com.arquivototal.gedtotalsignature.domain.enumeration.SignatureStepType;
import br.com.arquivototal.gedtotalsignature.infrastructure.http.SignatureDocumentPayload;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QualifiedIcpBrasilSignatureEngine implements SignatureEngine {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private final SignatureProperties signatureProperties;

    @Override
    public SignatureStepType supports() {
        return SignatureStepType.ASSINATURA_QUALIFICADA_ICP_BRASIL;
    }

    @Override
    public SignatureStepOutput apply(byte[] documentBytes, SignatureDocumentPayload payload, String traceId) {
        SignatureProperties.QualifiedIcpBrasil config = signatureProperties.qualifiedIcpBrasil();
        if (config == null || !config.enabled()) {
            throw new IllegalStateException("Assinatura qualificada ICP-Brasil nao esta habilitada no worker");
        }
        KeyMaterial keyMaterial = loadKeyMaterial(config);
        byte[] signedBytes = signPdf(documentBytes, keyMaterial, config, traceId);
        String hash = HashUtils.sha256Hex(signedBytes);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("engine", "qualified-icp-brasil");
        metadata.put("certificateSubject", keyMaterial.certificate().getSubjectX500Principal().getName());
        metadata.put("certificateSerial", keyMaterial.certificate().getSerialNumber().toString(16));
        metadata.put("traceId", traceId);
        metadata.put("outputHash", hash);
        return new SignatureStepOutput(signedBytes, "memory://qualified-icp/" + hash, metadata);
    }

    private byte[] signPdf(
        byte[] documentBytes,
        KeyMaterial keyMaterial,
        SignatureProperties.QualifiedIcpBrasil config,
        String traceId
    ) {
        try (PDDocument document = Loader.loadPDF(documentBytes); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDSignature signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            signature.setName(config.signerName() != null ? config.signerName() : "GedTotal ICP-Brasil");
            signature.setReason(config.reason() != null ? config.reason() : "Assinatura qualificada ICP-Brasil");
            signature.setLocation(config.location());
            signature.setSignDate(Calendar.getInstance());

            SignatureOptions options = new SignatureOptions();
            options.setPreferredSignatureSize(SignatureOptions.DEFAULT_SIGNATURE_SIZE * 2);
            SignatureInterface signatureInterface = content -> signCms(content, keyMaterial);

            document.addSignature(signature, signatureInterface, options);
            document.getDocumentInformation().setCustomMetadataValue("gedtotal-trace-id", traceId);
            document.saveIncremental(output);
            options.close();
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao aplicar assinatura qualificada ICP-Brasil", ex);
        }
    }

    private byte[] signCms(InputStream content, KeyMaterial keyMaterial) throws Exception {
        List<X509Certificate> certificateChain = Arrays.stream(keyMaterial.chain()).map(X509Certificate.class::cast).toList();
        DigestCalculatorProvider digestProvider = new JcaDigestCalculatorProviderBuilder().setProvider(BouncyCastleProvider.PROVIDER_NAME).build();
        ContentSigner contentSigner = new JcaContentSignerBuilder(resolveSignatureAlgorithm(keyMaterial.privateKey()))
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .build(keyMaterial.privateKey());

        CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
        generator.addSignerInfoGenerator(
            new JcaSignerInfoGeneratorBuilder(digestProvider).build(contentSigner, keyMaterial.certificate())
        );
        generator.addCertificates(new JcaCertStore(certificateChain));
        CMSTypedData cmsTypedData = new CMSProcessableByteArray(content.readAllBytes());
        CMSSignedData signedData = generator.generate(cmsTypedData, false);
        return signedData.getEncoded();
    }

    private String resolveSignatureAlgorithm(PrivateKey privateKey) {
        return switch (privateKey.getAlgorithm()) {
            case "RSA" -> "SHA256withRSA";
            case "EC", "ECDSA" -> "SHA256withECDSA";
            default -> throw new IllegalStateException("Algoritmo de chave privada nao suportado: " + privateKey.getAlgorithm());
        };
    }

    private KeyMaterial loadKeyMaterial(SignatureProperties.QualifiedIcpBrasil config) {
        try {
            if (config.keyStorePath() == null || config.keyStorePath().isBlank()) {
                throw new IllegalStateException("keyStorePath nao configurado para assinatura qualificada");
            }
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (InputStream inputStream = Files.newInputStream(Path.of(config.keyStorePath()))) {
                keyStore.load(inputStream, config.keyStorePassword().toCharArray());
            }

            String alias = resolveAlias(keyStore, config.keyAlias());
            char[] keyPassword = (config.keyPassword() != null && !config.keyPassword().isBlank()
                    ? config.keyPassword()
                    : config.keyStorePassword())
                .toCharArray();
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, keyPassword);
            Certificate[] chain = keyStore.getCertificateChain(alias);
            X509Certificate certificate = (X509Certificate) chain[0];
            return new KeyMaterial(privateKey, certificate, chain);
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao carregar certificado ICP-Brasil", ex);
        }
    }

    private String resolveAlias(KeyStore keyStore, String configuredAlias) throws Exception {
        if (configuredAlias != null && !configuredAlias.isBlank()) {
            return configuredAlias;
        }
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (keyStore.isKeyEntry(alias)) {
                return alias;
            }
        }
        throw new IllegalStateException("Nenhuma chave privada encontrada no PKCS12 configurado");
    }

    private record KeyMaterial(PrivateKey privateKey, X509Certificate certificate, Certificate[] chain) {}
}
