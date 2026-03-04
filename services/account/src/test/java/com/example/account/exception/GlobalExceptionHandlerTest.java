package com.example.account.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.core.MethodParameter;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    // ==================== ResourceNotFoundException tests ====================

    @Test
    void handleResourceNotFoundException_returnsNotFoundStatus() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Account not found with id: 1");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleResourceNotFoundException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getMessage()).isEqualTo("Account not found with id: 1");
        assertThat(response.getBody().getTimestamp()).isNotNull();
    }

    @Test
    void handleResourceNotFoundException_differentMessage_returnsCorrectMessage() {
        ResourceNotFoundException ex = new ResourceNotFoundException("User not found with id: 999");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleResourceNotFoundException(ex);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("User not found with id: 999");
    }

    // ==================== InsufficientFundsException tests ====================

    @Test
    void handleInsufficientFundsException_returnsBadRequestStatus() {
        InsufficientFundsException ex = new InsufficientFundsException("Insufficient funds in account");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleInsufficientFundsException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).isEqualTo("Insufficient funds in account");
        assertThat(response.getBody().getTimestamp()).isNotNull();
    }

    // ==================== MethodArgumentNotValidException tests ====================

    @Test
    void handleValidationExceptions_singleFieldError_returnsBadRequestWithErrors() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "accountRequestDto");
        bindingResult.addError(new FieldError("accountRequestDto", "userId", "must not be null"));

        MethodParameter methodParameter = new MethodParameter(
                this.getClass().getDeclaredMethod("handleValidationExceptions_singleFieldError_returnsBadRequestWithErrors"), -1
        );
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<Map<String, String>> response = exceptionHandler.handleValidationExceptions(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsEntry("userId", "must not be null");
    }

    @Test
    void handleValidationExceptions_multipleFieldErrors_returnsBadRequestWithAllErrors() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "accountRequestDto");
        bindingResult.addError(new FieldError("accountRequestDto", "userId", "must not be null"));
        bindingResult.addError(new FieldError("accountRequestDto", "currency", "must not be null"));
        bindingResult.addError(new FieldError("accountRequestDto", "accountType", "must not be null"));

        MethodParameter methodParameter = new MethodParameter(
                this.getClass().getDeclaredMethod("handleValidationExceptions_multipleFieldErrors_returnsBadRequestWithAllErrors"), -1
        );
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<Map<String, String>> response = exceptionHandler.handleValidationExceptions(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(3);
        assertThat(response.getBody()).containsEntry("userId", "must not be null");
        assertThat(response.getBody()).containsEntry("currency", "must not be null");
        assertThat(response.getBody()).containsEntry("accountType", "must not be null");
    }

    // ==================== Generic Exception tests ====================

    @Test
    void handleGenericException_returnsInternalServerError() {
        Exception ex = new Exception("Something went wrong");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGenericException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(500);
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred: Something went wrong");
        assertThat(response.getBody().getTimestamp()).isNotNull();
    }

    @Test
    void handleGenericException_nullMessage_returnsErrorWithNullInMessage() {
        Exception ex = new Exception((String) null);

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGenericException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred: null");
    }
}
