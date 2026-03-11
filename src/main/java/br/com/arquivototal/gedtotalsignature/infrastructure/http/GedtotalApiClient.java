package br.com.arquivototal.gedtotalsignature.infrastructure.http;

import br.com.arquivototal.gedtotalsignature.config.InternalApiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class GedtotalApiClient {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private final RestClient.Builder restClientBuilder;
    private final InternalApiProperties internalApiProperties;

    public SignatureDocumentPayload fetchPayload(String payloadUrl) {
        log.info("Buscando payload do gedtotalapi url={}", payloadUrl);
        return restClientBuilder
            .baseUrl(internalApiProperties.gedtotalapiBaseUrl())
            .build()
            .get()
            .uri(payloadUrl)
            .header(INTERNAL_TOKEN_HEADER, internalTokenHeader())
            .retrieve()
            .body(SignatureDocumentPayload.class);
    }

    public byte[] fetchDocumentContent(String contentUrl) {
        log.info("Baixando conteudo do documento url={}", contentUrl);
        return restClientBuilder
            .baseUrl(internalApiProperties.gedtotalapiBaseUrl())
            .build()
            .get()
            .uri(contentUrl)
            .header(INTERNAL_TOKEN_HEADER, internalTokenHeader())
            .retrieve()
            .body(byte[].class);
    }

    public CustodiaArtifactResponse uploadArtifact(Long arquivoId, byte[] content, String nomeArquivo, String etapa) {
        log.info("Enviando artefato assinado ao gedtotalapi arquivoId={} etapa={} bytes={}", arquivoId, etapa, content.length);
        return restClientBuilder
            .baseUrl(internalApiProperties.gedtotalapiBaseUrl())
            .build()
            .post()
            .uri(uriBuilder ->
                uriBuilder
                    .path("/api/internal/custodia/documentos/{arquivoId}/artifacts")
                    .queryParam("contentType", "application/pdf")
                    .queryParam("nomeArquivo", nomeArquivo)
                    .queryParam("etapa", etapa)
                    .build(arquivoId)
            )
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(INTERNAL_TOKEN_HEADER, internalTokenHeader())
            .body(content)
            .retrieve()
            .body(CustodiaArtifactResponse.class);
    }

    private String internalTokenHeader() {
        String token = internalApiProperties.internalToken();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("app.internal-api.internal-token nao configurado");
        }
        return token;
    }
}
