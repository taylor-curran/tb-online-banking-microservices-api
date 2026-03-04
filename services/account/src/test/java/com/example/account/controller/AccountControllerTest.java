package com.example.account.controller;

import com.example.account.dto.AccountRequestDto;
import com.example.account.dto.AccountResponseDto;
import com.example.account.exception.GlobalExceptionHandler;
import com.example.account.exception.ResourceNotFoundException;
import com.example.account.model.Account;
import com.example.account.service.AccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AccountService accountService;

    @InjectMocks
    private AccountController accountController;

    private ObjectMapper objectMapper;

    private AccountResponseDto responseDto;
    private AccountRequestDto requestDto;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(accountController, "accountService", accountService);
        mockMvc = MockMvcBuilders.standaloneSetup(accountController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        responseDto = AccountResponseDto.builder()
                .id(1L)
                .accountNumber("AC1234567890")
                .userId(100L)
                .accountType(Account.AccountType.SAVINGS)
                .balance(new BigDecimal("1000.00"))
                .currency("USD")
                .createdAt(LocalDateTime.of(2025, 1, 1, 10, 0))
                .isActive(true)
                .build();

        requestDto = AccountRequestDto.builder()
                .userId(100L)
                .accountType(Account.AccountType.SAVINGS)
                .currency("USD")
                .initialDeposit(new BigDecimal("1000.00"))
                .build();
    }

    // ==================== POST /api/v1/accounts ====================

    @Test
    void createAccount_validRequest_returnsCreatedStatus() throws Exception {
        when(accountService.createAccount(any(AccountRequestDto.class))).thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.accountNumber").value("AC1234567890"))
                .andExpect(jsonPath("$.userId").value(100))
                .andExpect(jsonPath("$.accountType").value("SAVINGS"))
                .andExpect(jsonPath("$.balance").value(1000.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.isActive").value(true));

        verify(accountService).createAccount(any(AccountRequestDto.class));
    }

    @Test
    void createAccount_missingUserId_returnsBadRequest() throws Exception {
        AccountRequestDto invalidRequest = AccountRequestDto.builder()
                .accountType(Account.AccountType.SAVINGS)
                .currency("USD")
                .initialDeposit(new BigDecimal("500.00"))
                .build();

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(accountService, never()).createAccount(any(AccountRequestDto.class));
    }

    @Test
    void createAccount_missingAccountType_returnsBadRequest() throws Exception {
        AccountRequestDto invalidRequest = AccountRequestDto.builder()
                .userId(100L)
                .currency("USD")
                .initialDeposit(new BigDecimal("500.00"))
                .build();

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(accountService, never()).createAccount(any(AccountRequestDto.class));
    }

    @Test
    void createAccount_missingCurrency_returnsBadRequest() throws Exception {
        AccountRequestDto invalidRequest = AccountRequestDto.builder()
                .userId(100L)
                .accountType(Account.AccountType.CHECKING)
                .initialDeposit(new BigDecimal("500.00"))
                .build();

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(accountService, never()).createAccount(any(AccountRequestDto.class));
    }

    @Test
    void createAccount_negativeInitialDeposit_returnsBadRequest() throws Exception {
        AccountRequestDto invalidRequest = AccountRequestDto.builder()
                .userId(100L)
                .accountType(Account.AccountType.SAVINGS)
                .currency("USD")
                .initialDeposit(new BigDecimal("-100.00"))
                .build();

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(accountService, never()).createAccount(any(AccountRequestDto.class));
    }

    @Test
    void createAccount_serviceThrowsResourceNotFound_returnsNotFound() throws Exception {
        when(accountService.createAccount(any(AccountRequestDto.class)))
                .thenThrow(new ResourceNotFoundException("User not found with id: 100"));

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found with id: 100"));
    }

    @Test
    void createAccount_serviceThrowsIllegalArgument_returnsInternalServerError() throws Exception {
        when(accountService.createAccount(any(AccountRequestDto.class)))
                .thenThrow(new IllegalArgumentException("User already has an account."));

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isInternalServerError());
    }

    // ==================== GET /api/v1/accounts ====================

    @Test
    void getAllAccounts_accountsExist_returnsOkWithList() throws Exception {
        AccountResponseDto responseDto2 = AccountResponseDto.builder()
                .id(2L)
                .accountNumber("AC0987654321")
                .userId(200L)
                .accountType(Account.AccountType.CHECKING)
                .balance(new BigDecimal("2500.00"))
                .currency("EUR")
                .createdAt(LocalDateTime.of(2025, 2, 1, 12, 0))
                .isActive(true)
                .build();

        when(accountService.getAllAccounts()).thenReturn(List.of(responseDto, responseDto2));

        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].accountNumber").value("AC1234567890"))
                .andExpect(jsonPath("$[1].accountNumber").value("AC0987654321"));

        verify(accountService).getAllAccounts();
    }

    @Test
    void getAllAccounts_noAccounts_returnsOkWithEmptyList() throws Exception {
        when(accountService.getAllAccounts()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        verify(accountService).getAllAccounts();
    }

    // ==================== GET /api/v1/accounts/{id} ====================

    @Test
    void getAccountById_existingId_returnsOkWithAccount() throws Exception {
        when(accountService.getAccountById(1L)).thenReturn(responseDto);

        mockMvc.perform(get("/api/v1/accounts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.accountNumber").value("AC1234567890"))
                .andExpect(jsonPath("$.userId").value(100))
                .andExpect(jsonPath("$.accountType").value("SAVINGS"))
                .andExpect(jsonPath("$.balance").value(1000.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.isActive").value(true));

        verify(accountService).getAccountById(1L);
    }

    @Test
    void getAccountById_nonExistingId_returnsNotFound() throws Exception {
        when(accountService.getAccountById(999L))
                .thenThrow(new ResourceNotFoundException("Account not found with id: 999"));

        mockMvc.perform(get("/api/v1/accounts/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found with id: 999"));

        verify(accountService).getAccountById(999L);
    }

    // ==================== GET /api/v1/accounts/number/{accountNumber} ====================

    @Test
    void getAccountByNumber_existingNumber_returnsOkWithAccount() throws Exception {
        when(accountService.getAccountByNumber("AC1234567890")).thenReturn(responseDto);

        mockMvc.perform(get("/api/v1/accounts/number/AC1234567890"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("AC1234567890"));

        verify(accountService).getAccountByNumber("AC1234567890");
    }

    @Test
    void getAccountByNumber_nonExistingNumber_returnsNotFound() throws Exception {
        when(accountService.getAccountByNumber("ACNONEXIST"))
                .thenThrow(new ResourceNotFoundException("Account not found with number: ACNONEXIST"));

        mockMvc.perform(get("/api/v1/accounts/number/ACNONEXIST"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found with number: ACNONEXIST"));

        verify(accountService).getAccountByNumber("ACNONEXIST");
    }

    // ==================== GET /api/v1/accounts/user/{userId} ====================

    @Test
    void getAccountsByUserId_userHasAccounts_returnsOkWithList() throws Exception {
        when(accountService.getAccountsByUserId(100L)).thenReturn(List.of(responseDto));

        mockMvc.perform(get("/api/v1/accounts/user/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId").value(100));

        verify(accountService).getAccountsByUserId(100L);
    }

    @Test
    void getAccountsByUserId_userHasNoAccounts_returnsOkWithEmptyList() throws Exception {
        when(accountService.getAccountsByUserId(999L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/accounts/user/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        verify(accountService).getAccountsByUserId(999L);
    }

    // ==================== GET /api/v1/accounts/user/{userId}/active ====================

    @Test
    void getActiveAccountsByUserId_userHasActiveAccounts_returnsOkWithList() throws Exception {
        when(accountService.getActiveAccountsByUserId(100L)).thenReturn(List.of(responseDto));

        mockMvc.perform(get("/api/v1/accounts/user/100/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].isActive").value(true));

        verify(accountService).getActiveAccountsByUserId(100L);
    }

    @Test
    void getActiveAccountsByUserId_userHasNoActiveAccounts_returnsOkWithEmptyList() throws Exception {
        when(accountService.getActiveAccountsByUserId(100L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/accounts/user/100/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        verify(accountService).getActiveAccountsByUserId(100L);
    }

    // ==================== PUT /api/v1/accounts/{id}/deactivate ====================

    @Test
    void deactivateAccount_existingId_returnsOkWithDeactivatedAccount() throws Exception {
        AccountResponseDto deactivatedResponse = AccountResponseDto.builder()
                .id(1L)
                .accountNumber("AC1234567890")
                .userId(100L)
                .accountType(Account.AccountType.SAVINGS)
                .balance(new BigDecimal("1000.00"))
                .currency("USD")
                .createdAt(LocalDateTime.of(2025, 1, 1, 10, 0))
                .isActive(false)
                .build();

        when(accountService.deactivateAccount(1L)).thenReturn(deactivatedResponse);

        mockMvc.perform(put("/api/v1/accounts/1/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.isActive").value(false));

        verify(accountService).deactivateAccount(1L);
    }

    @Test
    void deactivateAccount_nonExistingId_returnsNotFound() throws Exception {
        when(accountService.deactivateAccount(999L))
                .thenThrow(new ResourceNotFoundException("Account not found with id: 999"));

        mockMvc.perform(put("/api/v1/accounts/999/deactivate"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found with id: 999"));

        verify(accountService).deactivateAccount(999L);
    }

    // ==================== PUT /api/v1/accounts/{id}/balance ====================

    @Test
    void updateBalance_existingId_returnsOkWithUpdatedBalance() throws Exception {
        AccountResponseDto updatedResponse = AccountResponseDto.builder()
                .id(1L)
                .accountNumber("AC1234567890")
                .userId(100L)
                .accountType(Account.AccountType.SAVINGS)
                .balance(new BigDecimal("5000.00"))
                .currency("USD")
                .createdAt(LocalDateTime.of(2025, 1, 1, 10, 0))
                .isActive(true)
                .build();

        when(accountService.updateBalance(eq(1L), any(BigDecimal.class))).thenReturn(updatedResponse);

        mockMvc.perform(put("/api/v1/accounts/1/balance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("5000.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(5000.00));

        verify(accountService).updateBalance(eq(1L), any(BigDecimal.class));
    }

    @Test
    void updateBalance_nonExistingId_returnsNotFound() throws Exception {
        when(accountService.updateBalance(eq(999L), any(BigDecimal.class)))
                .thenThrow(new ResourceNotFoundException("Account not found with id: 999"));

        mockMvc.perform(put("/api/v1/accounts/999/balance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("5000.00"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found with id: 999"));

        verify(accountService).updateBalance(eq(999L), any(BigDecimal.class));
    }
}
