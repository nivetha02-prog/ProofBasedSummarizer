package com.proofsum;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Start the Spring Boot application and expose the ProofSum services and REST API.
 */
@SpringBootApplication
public class ProofSumApplication {
    /**
     * Launch the application with the supplied Spring Boot arguments.
     *
     * @param args command-line arguments passed to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(ProofSumApplication.class, args);
    }
}
