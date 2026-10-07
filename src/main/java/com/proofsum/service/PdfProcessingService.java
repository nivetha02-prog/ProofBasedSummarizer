package com.proofsum.service;

import com.proofsum.model.PageText;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Validates the uploaded PDF, stores it temporarily and extracts text page by page
 * using Apache PDFBox so that every passage keeps its page number.
 */
@Service
public class PdfProcessingService {

    private final Path storageDir;

    public PdfProcessingService(@Value("${proofsum.storage.dir}") String storageDir) throws IOException {
        this.storageDir = Paths.get(storageDir).toAbsolutePath();
        Files.createDirectories(this.storageDir);
    }

    public Path store(MultipartFile file, String documentId) throws IOException {
        validate(file);
        Path target = storageDir.resolve(documentId + ".pdf");
        Files.copy(file.getInputStream(), target);
        return target;
    }

    public List<PageText> extractPages(Path pdfPath) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
            if (doc.isEncrypted()) {
                throw new IllegalArgumentException("The PDF is encrypted and cannot be read.");
            }
            int pageCount = doc.getNumberOfPages();
            if (pageCount == 0) {
                throw new IllegalArgumentException("The PDF contains no pages.");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            List<PageText> pages = new ArrayList<>(pageCount);
            int nonEmpty = 0;

            for (int p = 1; p <= pageCount; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String text = stripper.getText(doc);
                if (text != null && !text.isBlank()) nonEmpty++;
                pages.add(new PageText(p, text == null ? "" : text));
            }

            if (nonEmpty == 0) {
                throw new IllegalArgumentException(
                        "No extractable text found. The PDF may be scanned images; run OCR first.");
            }
            return pages;
        } catch (IOException e) {
            throw new IllegalArgumentException("The PDF appears to be corrupted or unreadable: " + e.getMessage(), e);
        }
    }

    public void delete(Path pdfPath) {
        try { Files.deleteIfExists(pdfPath); } catch (IOException ignored) { }
    }

    private void validate(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file was uploaded or the file is empty.");
        }
        String name = file.getOriginalFilename();
        boolean pdfByName = name != null && name.toLowerCase().endsWith(".pdf");
        boolean pdfByType = "application/pdf".equalsIgnoreCase(file.getContentType());
        if (!pdfByName && !pdfByType) {
            throw new IllegalArgumentException("Only PDF files are accepted.");
        }
        byte[] head = file.getInputStream().readNBytes(5);
        if (head.length < 5 || !new String(head).startsWith("%PDF-")) {
            throw new IllegalArgumentException("The file is not a valid PDF (missing %PDF header).");
        }
    }

    public static String newDocumentId() {
        return UUID.randomUUID().toString();
    }
}
