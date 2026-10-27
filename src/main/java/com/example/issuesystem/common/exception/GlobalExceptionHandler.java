package com.example.issuesystem.common.exception;

import com.example.issuesystem.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Bad request: {}", e.getMessage());

        return ResponseEntity.badRequest()
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .message(e.getMessage())
                        .data(null)
                        .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("잘못된 요청입니다.");

        log.warn("Validation failed: {}", message);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .message(message)
                        .data(null)
                        .build());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccessException(DataAccessException e) {
        log.error("Database error occurred.", e);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .message("데이터베이스 처리 중 오류가 발생했습니다.")
                        .data(null)
                        .build());
    }

    @ExceptionHandler({
            HttpMessageNotWritableException.class,
            AsyncRequestNotUsableException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleResponseWriteException(Exception e) {
        if (isClientAbort(e)) {
            log.debug("Client disconnected before the response was written: {}", rootCauseMessage(e));
            return ResponseEntity.status(HttpStatusCode.valueOf(499)).build();
        }

        log.error("Response write failed.", e);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .message("서버 내부 오류가 발생했습니다.")
                        .data(null)
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        if (isClientAbort(e)) {
            log.debug("Client disconnected before the response was written: {}", rootCauseMessage(e));
            return ResponseEntity.status(HttpStatusCode.valueOf(499)).build();
        }

        log.error("Unhandled exception occurred.", e);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .message("서버 내부 오류가 발생했습니다.")
                        .data(null)
                        .build());
    }

    private boolean isClientAbort(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String className = current.getClass().getName();
            String message = current.getMessage();
            if (className.equals("org.apache.catalina.connector.ClientAbortException")
                    || current instanceof AsyncRequestNotUsableException
                    || isBrokenPipe(current)
                    || containsIgnoreCase(message, "broken pipe")
                    || containsIgnoreCase(message, "connection reset")
                    || containsIgnoreCase(message, "connection aborted")
                    || containsIgnoreCase(message, "connection closed")
                    || containsIgnoreCase(message, "파이프가 깨어짐")) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    private boolean isBrokenPipe(Throwable throwable) {
        return throwable instanceof IOException
                && (containsIgnoreCase(throwable.getMessage(), "broken pipe")
                || containsIgnoreCase(throwable.getMessage(), "파이프가 깨어짐"));
    }

    private boolean containsIgnoreCase(String value, String expected) {
        return value != null && value.toLowerCase().contains(expected.toLowerCase());
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        Throwable root = throwable;
        while (current != null) {
            root = current;
            current = current.getCause();
        }

        return root == null ? "" : root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
