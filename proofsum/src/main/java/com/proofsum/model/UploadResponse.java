package com.proofsum.model;

import java.util.List;

public record UploadResponse(
        String documentId,
        String fileName,
        int pages,
        int chunks,
        List<String> sections
) {}
