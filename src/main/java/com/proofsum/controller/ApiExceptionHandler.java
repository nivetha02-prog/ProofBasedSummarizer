package com.proofsum.controller;

import com.proofsum.model.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Turns exceptions into clean JSON errors the frontend can display. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> badPdf(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError("invalid_pdf", e.getMessage()));
    }

    @ExceptionHandler(PaperController.DocumentNotFoundException.class)
    public ResponseEntity<ApiError> notFound(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("document_not_found", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(new ApiError("validation", "documentId and question are required."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new ApiError("file_too_large", "PDF must be under 50 MB."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> generic(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError("server_error", e.getMessage()));
    }
}
