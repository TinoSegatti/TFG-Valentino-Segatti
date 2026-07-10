package com.reforma.domain.common.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        var fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(java.util.stream.Collectors.toMap(
                        e -> e.getField(),
                        e -> e.getDefaultMessage() != null ? e.getDefaultMessage() : "inválido",
                        (a, b) -> a));
        var body = new ApiError(
                java.time.Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                "Error de validación",
                request.getRequestURI(),
                fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(
                        HttpStatus.BAD_REQUEST.value(),
                        "Bad Request",
                        ex.getMessage(),
                        request.getRequestURI()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(
            ResponseStatusException ex, HttpServletRequest request) {
        int code = ex.getStatusCode().value();
        HttpStatus status = HttpStatus.resolve(code);
        String reason = status != null ? status.getReasonPhrase() : "Error";
        String message = ex.getReason() != null ? ex.getReason() : reason;
        return ResponseEntity.status(code)
                .body(ApiError.of(code, reason, message, request.getRequestURI()));
    }

    /**
     * Precondición de empleados al contratar/programar un plan (RD-P6.b.1): 409 con payload
     * estructurado para que el frontend arme el modal "Gestionar equipo".
     */
    @ExceptionHandler(com.reforma.domain.suscripciones.domain.EmpleadosSobreLimiteException.class)
    public ResponseEntity<ApiError> handleEmpleadosSobreLimite(
            com.reforma.domain.suscripciones.domain.EmpleadosSobreLimiteException ex,
            HttpServletRequest request) {
        var body = new ApiError(
                java.time.Instant.now(),
                HttpStatus.CONFLICT.value(),
                "Conflict",
                ex.getMessage(),
                request.getRequestURI(),
                Map.of(
                        "planDestino", ex.getPlanDestino().name(),
                        "empleadosActivos", String.valueOf(ex.getEmpleadosActivos()),
                        "limiteDestino", String.valueOf(ex.getLimiteDestino()),
                        "excedente", String.valueOf(ex.getExcedente())));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler({
        org.springframework.orm.ObjectOptimisticLockingFailureException.class,
        org.hibernate.StaleObjectStateException.class
    })
    public ResponseEntity<ApiError> handleOptimisticLock(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(
                        HttpStatus.CONFLICT.value(),
                        "Conflict",
                        "El registro fue modificado por otra operacion. Recargue e intente de nuevo.",
                        request.getRequestURI()));
    }
}
