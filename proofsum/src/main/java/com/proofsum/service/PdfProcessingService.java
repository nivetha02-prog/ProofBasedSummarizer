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
 * Validate uploaded PDF content, temporarily store accepted files, and extract page-level text.
 */
@Service
public class PdfProcessingService {

    /** Absolute directory used for temporary PDF copies. */
    private final Path storageDir;

    /**
     * Create the temporary storage directory when it does not already exist.
     *
     * @param storageDir configured directory for temporary PDF files
     * @throws IOException if the directory cannot be created
     */
    public PdfProcessingService(@Value("${proofsum.storage.dir}") String storageDir) throws IOException {
        this.storageDir = Paths.get(storageDir).toAbsolutePath();
        Files.createDirectories(this.storageDir);
    }

    /**
     * Validate and copy an uploaded PDF to temporary storage.
     *
     * @param file uploaded file; must be non-null, non-empty, and have a PDF name or media type
     *             and a {@code %PDF-} header
     * @param documentId identifier used to name the stored file
     * @return path to the temporary PDF copy
     * @throws IOException if the upload stream cannot be read or the copy cannot be written
     * @throws IllegalArgumentException if the file is absent, empty, not identified as a PDF,
     *                                  or lacks a PDF header
     */
    public Path store(MultipartFile file, String documentId) throws IOException {
        validate(file);
        Path target = storageDir.resolve(documentId + ".pdf");
        Files.copy(file.getInputStream(), target);
        return target;
    }

    /**
     * Extract text for each page of a readable, non-empty PDF.
     *
     * @param pdfPath path to the PDF to read
     * @return page text entries in document order, including entries for blank pages
     * @throws IllegalArgumentException if the PDF is encrypted, has no pages, has no extractable
     *                                  text, or cannot be read as a PDF
     * @throws IOException declared by the method signature; I/O errors caught during PDF processing
     *                      are wrapped in {@link IllegalArgumentException}
     */
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

    /**
     * Attempt to remove a temporary PDF copy.
     *
     * @param pdfPath path to the file to remove; absence is allowed
     */
    public void delete(Path pdfPath) {
        try { Files.deleteIfExists(pdfPath); } catch (IOException ignored) { }
    }

    /**
     * Check the upload is present, PDF-identified, and begins with a PDF signature.
     *
     * @param file uploaded file to inspect
     * @throws IOException if reading the upload stream fails
     * @throws IllegalArgumentException if the file is null or empty, is not identified as a PDF,
     *                                  or lacks the {@code %PDF-} signature
     */
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

    /**
     * Generate a random UUID string suitable for identifying a new document.
     *
     * @return a newly generated UUID string
     */
    public static String newDocumentId() {
        return UUID.randomUUID().toString();
    }
}
