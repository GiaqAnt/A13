package com.robotchallenge.t8.controller.advice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Controller advice che intercetta e gestisce le eccezioni sollevate
 * durante l'esecuzione dei task con EvoSuite.
 */
@ControllerAdvice
public class TaskExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(TaskExceptionHandler.class);

    @ExceptionHandler(InterruptedException.class)
    public ResponseEntity<String> handleInterruptedException(InterruptedException e) {
        Thread.currentThread().interrupt();
        logger.error("[CoverageControllerAdvice] Il processo è stato interrotto", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Il processo è stato interrotto.");
    }

    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<String> handleTimeoutException(TimeoutException e) {
        logger.warn("[CoverageControllerAdvice] Timeout superato", e);
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body("Il task ha superato il tempo massimo disponibile.");
    }

    @ExceptionHandler(RejectedExecutionException.class)
    public ResponseEntity<String> handleRejectedExecutionException(RejectedExecutionException e) {
        logger.warn("[CoverageControllerAdvice] Coda piena: impossibile accettare nuovi task", e);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body("Troppe richieste in coda. Riprova più tardi.");
    }

    @ExceptionHandler(ExecutionException.class)
    public ResponseEntity<String> handleExecutionException(ExecutionException e) {
        logger.error("[CoverageControllerAdvice] Errore durante la compilazione o l'esecuzione", e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("Errore durante la compilazione o l'esecuzione.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(Exception e) {
        logger.error("[CoverageControllerAdvice] Errore generico", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Si è verificato un errore imprevisto.");
    }
}
