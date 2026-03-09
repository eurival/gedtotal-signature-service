package br.com.arquivototal.gedtotalsignature.application.service.engine;

import br.com.arquivototal.gedtotalsignature.domain.enumeration.SignatureStepType;
import br.com.arquivototal.gedtotalsignature.infrastructure.http.SignatureDocumentPayload;

public interface SignatureEngine {
    SignatureStepType supports();

    SignatureStepOutput apply(byte[] documentBytes, SignatureDocumentPayload payload, String traceId);
}
