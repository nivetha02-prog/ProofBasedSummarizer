package com.proofsum.model;

/**
 * Represent the error code and explanatory detail returned by the REST API.
 *
 * @param error stable error identifier
 * @param detail human-readable error detail
 */
public record ApiError(String error, String detail) {}
