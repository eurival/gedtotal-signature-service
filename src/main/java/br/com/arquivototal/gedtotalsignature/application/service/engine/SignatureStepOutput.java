package br.com.arquivototal.gedtotalsignature.application.service.engine;

import java.util.Map;

public record SignatureStepOutput(byte[] documentBytes, String artefatoRef, Map<String, Object> metadata) {}
