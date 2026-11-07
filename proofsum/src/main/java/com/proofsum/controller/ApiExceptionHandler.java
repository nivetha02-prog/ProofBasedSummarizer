package com.proofsum.controller;

import com.proofsum.model.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Translate controller and request-processing exceptions into structured JSON error responses.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * Map invalid upload or PDF argument errors to HTTP 400.
     *
     * @param e exception containing the validation or processing detail
     * @return response with error code {@code invalid_pdf}
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> badPdf(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError("invalid_pdf", e.getMessage()));
    }

    /**
     * Map a missing document error to HTTP 404.
     *
     * @param e exception containing the not-found detail
     * @return response with error code {@code document_not_found}
     */
    @ExceptionHandler(PaperController.DocumentNotFoundException.class)
    public ResponseEntity<ApiError> notFound(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("document_not_found", e.getMessage()));
    }

    /**
     * Map bean-validation failures to HTTP 400.
     *
     * @param e exception describing request validation failures
     * @return response with error code {@code validation}
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(new ApiError("validation", "documentId and question are required."));
    }

    /**
     * Map upload-size violations to HTTP 413.
     *
     * @param e exception raised when the configured upload limit is exceeded
     * @return response with error code {@code file_too_large}
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new ApiError("file_too_large", "PDF must be under 50 MB."));
    }

    /**
     * Map otherwise unhandled exceptions to HTTP 500.
     *
     * @param e exception whose message is included in the error detail
     * @return response with error code {@code server_error}
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> generic(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError("server_error", e.getMessage()));
    }
}
