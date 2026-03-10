package br.com.arquivototal.gedtotalsignature.application.service.engine;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

final class PdfAppendSupport {

    private static final Color GEDTOTAL_BLUE = new Color(0, 88, 184);
    private static final Color LIGHT_PANEL = new Color(236, 243, 251);
    private static final Color TEXT_DARK = new Color(24, 32, 47);
    private static final float MARGIN = 22f;
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss z");

    private PdfAppendSupport() {}

    static byte[] appendStamp(byte[] source, SignatureVisualSpec spec) {
        try (PDDocument document = Loader.loadPDF(source); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            applyMetadata(document, spec);
            SignatureLayoutMode mode = SignatureLayoutMode.from(spec.mode());
            SignaturePosition position = SignaturePosition.from(spec.position());

            switch (mode) {
                case TODAS_AS_PAGINAS_HORIZONTAL -> {
                    for (PDPage page : document.getPages()) {
                        drawHorizontalStamp(document, page, spec, position);
                    }
                }
                case TODAS_AS_PAGINAS_VERTICAL -> {
                    for (PDPage page : document.getPages()) {
                        drawVerticalStamp(document, page, spec, position);
                    }
                }
                case ULTIMA_PAGINA_VERTICAL -> drawVerticalStamp(document, document.getPage(document.getNumberOfPages() - 1), spec, position);
                case PAGINA_CERTIFICADO -> {
                }
                case ULTIMA_PAGINA_E_CERTIFICADO -> drawHorizontalStamp(document, document.getPage(document.getNumberOfPages() - 1), spec, position);
                case ULTIMA_PAGINA_HORIZONTAL -> drawHorizontalStamp(document, document.getPage(document.getNumberOfPages() - 1), spec, position);
            }

            if (mode == SignatureLayoutMode.PAGINA_CERTIFICADO || spec.generateCertificatePage() || mode == SignatureLayoutMode.ULTIMA_PAGINA_E_CERTIFICADO) {
                appendCertificatePage(document, spec);
            }

            document.saveIncremental(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao aplicar append mode visual no PDF", ex);
        }
    }

    private static void applyMetadata(PDDocument document, SignatureVisualSpec spec) {
        PDDocumentInformation info = document.getDocumentInformation();
        info.setTitle(nonBlank(spec.fileName(), "Documento assinado GedTotal"));
        info.setAuthor(nonBlank(spec.signerName(), "GedTotal"));
        info.setSubject("Documento assinado e validavel pelo GedTotal");
        info.setKeywords(
            "GedTotal, assinatura digital, codigo de validacao, "
                + nonBlank(spec.validationCode(), "sem-codigo")
        );
        info.setCreator("GedTotal Signature Service");
        info.setProducer("GedTotal Signature Service");
        info.setCustomMetadataValue("gedtotal-validation-code", nonBlank(spec.validationCode(), ""));
        info.setCustomMetadataValue("gedtotal-validation-url", nonBlank(spec.validationUrl(), ""));
        info.setCustomMetadataValue("gedtotal-document-hash", nonBlank(spec.documentHash(), ""));
        info.setCustomMetadataValue("gedtotal-signature-type", spec.signatureKind());
        info.setModificationDate(java.util.GregorianCalendar.from(spec.signedAt().toOffsetDateTime().toZonedDateTime()));
    }

    private static void drawHorizontalStamp(PDDocument document, PDPage page, SignatureVisualSpec spec, SignaturePosition position)
        throws IOException {
        PDRectangle box = page.getMediaBox();
        float width = Math.min(280f, box.getWidth() - (MARGIN * 2));
        float height = 78f;
        float x = switch (position) {
            case RODAPE_ESQUERDO, LATERAL_ESQUERDA -> MARGIN;
            case RODAPE_DIREITO, LATERAL_DIREITA -> box.getWidth() - width - MARGIN;
        };
        float y = MARGIN;

        try (PDPageContentStream content = new PDPageContentStream(document, page, AppendMode.APPEND, true, true)) {
            content.setNonStrokingColor(LIGHT_PANEL);
            content.addRect(x, y, width, height);
            content.fill();

            content.setStrokingColor(GEDTOTAL_BLUE);
            content.setLineWidth(1.2f);
            content.addRect(x, y, width, height);
            content.stroke();

            writeText(content, true, 12, x + 10, y + height - 16, header(spec));
            writeText(content, false, 8, x + 10, y + height - 30, "Codigo: " + display(spec.includeValidationCode(), spec.validationCode()));
            writeText(content, false, 8, x + 10, y + height - 42, "Assinante: " + trim(spec.signerName(), 34));
            writeText(content, false, 8, x + 10, y + height - 54, "Data: " + spec.signedAt().format(DISPLAY_TIME));

            if (spec.includeHash()) {
                writeText(content, false, 7, x + 10, y + height - 66, "Hash: " + trim(spec.documentHash(), 44));
            }

            if (spec.includeQrCode()) {
                drawQrCode(document, content, x + width - 64, y + 8, 52, spec.validationUrl());
            }
        }
    }

    private static void drawVerticalStamp(PDDocument document, PDPage page, SignatureVisualSpec spec, SignaturePosition position)
        throws IOException {
        PDRectangle box = page.getMediaBox();
        float width = 70f;
        float height = Math.min(220f, box.getHeight() - (MARGIN * 2));
        float x = switch (position) {
            case LATERAL_ESQUERDA, RODAPE_ESQUERDO -> MARGIN;
            case LATERAL_DIREITA, RODAPE_DIREITO -> box.getWidth() - width - MARGIN;
        };
        float y = MARGIN;

        try (PDPageContentStream content = new PDPageContentStream(document, page, AppendMode.APPEND, true, true)) {
            content.setNonStrokingColor(LIGHT_PANEL);
            content.addRect(x, y, width, height);
            content.fill();
            content.setStrokingColor(GEDTOTAL_BLUE);
            content.setLineWidth(1.2f);
            content.addRect(x, y, width, height);
            content.stroke();

            writeRotatedText(content, true, 11, x + 18, y + 12, 90, shortHeader(spec));
            if (spec.includeValidationCode()) {
                writeRotatedText(content, false, 8, x + 34, y + 12, 90, trim(spec.validationCode(), 20));
            }
            if (spec.includeQrCode()) {
                drawQrCode(document, content, x + 8, y + height - 62, 52, spec.validationUrl());
            }
        }
    }

    private static void appendCertificatePage(PDDocument document, SignatureVisualSpec spec) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        PDRectangle box = page.getMediaBox();

        try (PDPageContentStream content = new PDPageContentStream(document, page, AppendMode.OVERWRITE, true, true)) {
            content.setNonStrokingColor(Color.WHITE);
            content.addRect(0, 0, box.getWidth(), box.getHeight());
            content.fill();

            content.setStrokingColor(GEDTOTAL_BLUE);
            content.setLineWidth(2f);
            content.addRect(18, 18, box.getWidth() - 36, box.getHeight() - 36);
            content.stroke();

            writeText(content, true, 20, 52, box.getHeight() - 56, "Certificado de Assinatura");
            writeText(content, false, 10, 52, box.getHeight() - 82, header(spec));
            writeText(content, false, 11, 52, box.getHeight() - 120, "Codigo de validacao");
            writeText(content, true, 15, 52, box.getHeight() - 138, display(spec.includeValidationCode(), spec.validationCode()));
            writeText(content, false, 10, 52, box.getHeight() - 168, "URL de validacao");
            writeText(content, false, 9, 52, box.getHeight() - 184, trim(nonBlank(spec.validationUrl(), "Nao disponivel"), 92));
            writeText(content, false, 10, 52, box.getHeight() - 214, "Assinante");
            writeText(content, false, 10, 52, box.getHeight() - 230, nonBlank(spec.signerName(), "GedTotal"));
            writeText(content, false, 10, 52, box.getHeight() - 258, "Data e hora");
            writeText(content, false, 10, 52, box.getHeight() - 274, spec.signedAt().format(DISPLAY_TIME));
            writeText(content, false, 10, 52, box.getHeight() - 302, "Hash do documento");
            writeWrappedText(content, 52, box.getHeight() - 320, 500, 11, 9, nonBlank(spec.documentHash(), "Nao informado"));
            writeWrappedText(
                content,
                52,
                box.getHeight() - 392,
                500,
                12,
                9,
                "Este documento possui identificador unico de validacao. A consulta publica deve confirmar integridade, signatarios e cadeia de evidencia registrada na esteira GedTotal."
            );

            if (spec.includeQrCode()) {
                drawQrCode(document, content, box.getWidth() - 164, box.getHeight() - 206, 96, spec.validationUrl());
            }
        }
    }

    private static void drawQrCode(PDDocument document, PDPageContentStream content, float x, float y, int size, String text) throws IOException {
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size);
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
            for (int row = 0; row < size; row++) {
                for (int col = 0; col < size; col++) {
                    image.setRGB(col, row, matrix.get(col, row) ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
                }
            }
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", png);
            PDImageXObject qr = LosslessFactory.createFromImage(document, image);
            content.drawImage(qr, x, y, size, size);
        } catch (Exception ex) {
            throw new IOException("Falha ao gerar QR code do certificado", ex);
        }
    }

    private static void writeText(PDPageContentStream content, boolean bold, int size, float x, float y, String text) throws IOException {
        content.beginText();
        content.setNonStrokingColor(TEXT_DARK);
        content.setFont(
            new PDType1Font(bold ? Standard14Fonts.FontName.HELVETICA_BOLD : Standard14Fonts.FontName.HELVETICA),
            size
        );
        content.newLineAtOffset(x, y);
        content.showText(trim(nonBlank(text, ""), 120));
        content.endText();
    }

    private static void writeWrappedText(PDPageContentStream content, float x, float y, float width, float lineHeight, int fontSize, String text)
        throws IOException {
        String[] words = nonBlank(text, "").split("\\s+");
        StringBuilder line = new StringBuilder();
        float cursorY = y;
        for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (candidate.length() > width / 5.2f) {
                writeText(content, false, fontSize, x, cursorY, line.toString());
                line = new StringBuilder(word);
                cursorY -= lineHeight;
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) {
            writeText(content, false, fontSize, x, cursorY, line.toString());
        }
    }

    private static void writeRotatedText(PDPageContentStream content, boolean bold, int size, float x, float y, double radians, String text)
        throws IOException {
        content.beginText();
        content.setNonStrokingColor(TEXT_DARK);
        content.setFont(
            new PDType1Font(bold ? Standard14Fonts.FontName.HELVETICA_BOLD : Standard14Fonts.FontName.HELVETICA),
            size
        );
        content.setTextMatrix(org.apache.pdfbox.util.Matrix.getRotateInstance(radians, x, y));
        content.showText(trim(nonBlank(text, ""), 36));
        content.endText();
    }

    private static String header(SignatureVisualSpec spec) {
        return switch (nonBlank(spec.templateVisual(), "GEDTOTAL_AZUL")) {
            case "ICPBRASIL_PADRAO" -> "ICP-Brasil | Documento assinado digitalmente";
            default -> "GedTotal | Documento assinado pelo sistema";
        };
    }

    private static String shortHeader(SignatureVisualSpec spec) {
        return switch (nonBlank(spec.templateVisual(), "GEDTOTAL_AZUL")) {
            case "ICPBRASIL_PADRAO" -> "ICP-Brasil";
            default -> "GedTotal";
        };
    }

    private static String display(boolean enabled, String value) {
        return enabled ? nonBlank(value, "Nao informado") : "Oculto pela politica";
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    enum SignatureLayoutMode {
        ULTIMA_PAGINA_HORIZONTAL,
        ULTIMA_PAGINA_VERTICAL,
        TODAS_AS_PAGINAS_VERTICAL,
        TODAS_AS_PAGINAS_HORIZONTAL,
        PAGINA_CERTIFICADO,
        ULTIMA_PAGINA_E_CERTIFICADO;

        static SignatureLayoutMode from(String value) {
            if (value == null || value.isBlank()) {
                return ULTIMA_PAGINA_E_CERTIFICADO;
            }
            return SignatureLayoutMode.valueOf(value);
        }
    }

    enum SignaturePosition {
        RODAPE_DIREITO,
        RODAPE_ESQUERDO,
        LATERAL_DIREITA,
        LATERAL_ESQUERDA;

        static SignaturePosition from(String value) {
            if (value == null || value.isBlank()) {
                return RODAPE_DIREITO;
            }
            return SignaturePosition.valueOf(value);
        }
    }

    record SignatureVisualSpec(
        String signatureKind,
        String signerName,
        String fileName,
        String documentHash,
        String validationCode,
        String validationUrl,
        String mode,
        String position,
        String templateVisual,
        boolean includeValidationCode,
        boolean includeQrCode,
        boolean includeHash,
        boolean includeSignatureData,
        boolean generateCertificatePage,
        ZonedDateTime signedAt
    ) {}
}
