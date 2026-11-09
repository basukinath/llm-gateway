package dev.basuki.llmgateway.exception;

import dev.basuki.llmgateway.model.GatewayError;
import dev.basuki.llmgateway.ratelimit.RateLimiter.RateLimitInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<GatewayError> handleRateLimit(RateLimitExceededException ex, HttpServletRequest request) {
        RateLimitInfo info = ex.getRateLimitInfo();
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("X-RateLimit-Limit", String.valueOf(info.limit()))
                .header("X-RateLimit-Remaining", "0")
                .header("X-RateLimit-Reset", String.valueOf(info.resetAtEpochSecond()))
                .header("Retry-After", String.valueOf(info.resetAtEpochSecond() - System.currentTimeMillis() / 1000))
                .body(new GatewayError(429, "Too Many Requests", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<GatewayError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");

        return ResponseEntity.badRequest()
                .body(new GatewayError(400, "Bad Request", message, request.getRequestURI()));
    }

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<GatewayError> handleProviderError(WebClientResponseException ex, HttpServletRequest request) {
        log.error("LLM provider returned error: {} {}", ex.getStatusCode(), ex.getResponseBodyAsString());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new GatewayError(502, "Bad Gateway",
                        "LLM provider error: " + ex.getStatusCode(), request.getRequestURI()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<GatewayError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(new GatewayError(400, "Bad Request", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GatewayError> handleGeneral(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error processing request to {}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new GatewayError(500, "Internal Server Error",
                        "An unexpected error occurred", request.getRequestURI()));
    }
}
