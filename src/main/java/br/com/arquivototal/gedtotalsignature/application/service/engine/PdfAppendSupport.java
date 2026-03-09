package br.com.arquivototal.gedtotalsignature.application.service.engine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZonedDateTime;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

final class PdfAppendSupport {

    private PdfAppendSupport() {}

    static byte[] appendStamp(byte[] source, String title, String bodyLine1, String bodyLine2) {
        try (PDDocument document = Loader.loadPDF(source); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = document.getPage(0);
            PDRectangle box = page.getMediaBox();
            float margin = 24f;
            float boxHeight = 44f;
            float startX = margin;
            float startY = margin + boxHeight;

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page, AppendMode.APPEND, true, true)) {
                contentStream.setNonStrokingColor(245, 245, 245);
                contentStream.addRect(margin, margin, Math.min(260f, box.getWidth() - (margin * 2)), boxHeight);
                contentStream.fill();

                contentStream.beginText();
                contentStream.setNonStrokingColor(40, 40, 40);
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 9);
                contentStream.newLineAtOffset(startX + 6, startY - 12);
                contentStream.showText(trim(title, 58));
                contentStream.endText();

                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8);
                contentStream.newLineAtOffset(startX + 6, startY - 24);
                contentStream.showText(trim(bodyLine1, 74));
                contentStream.endText();

                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8);
                contentStream.newLineAtOffset(startX + 6, startY - 34);
                contentStream.showText(trim(bodyLine2, 74));
                contentStream.endText();
            }

            document.getDocumentInformation().setModificationDate(java.util.GregorianCalendar.from(ZonedDateTime.now().toOffsetDateTime().toZonedDateTime()));
            document.saveIncremental(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao aplicar append mode no PDF", ex);
        }
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
