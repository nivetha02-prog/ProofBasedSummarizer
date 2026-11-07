package com.proofsum.model;

import java.util.List;

/**
 * Report the document identifier and processing counts after a successful upload.
 *
 * @param documentId identifier assigned to the uploaded document
 * @param fileName original uploaded file name
 * @param pages number of pages extracted from the PDF
 * @param chunks number of chunks indexed for retrieval
 * @param sections distinct section names represented by the chunks
 */
public record UploadResponse(
        String documentId,
        String fileName,
        int pages,
        int chunks,
        List<String> sections
) {}
