package com.example.account.service;

import com.example.account.auth.AuthClient;
import com.example.account.auth.AuthResponse;
import com.example.account.dto.AccountRequestDto;
import com.example.account.dto.AccountResponseDto;
import com.example.account.exception.ResourceNotFoundException;
import com.example.account.model.Account;
import com.example.account.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountEventPublisher accountEventPublisher;

    @Mock
    private AuthClient authClient;

    @InjectMocks
    private AccountService accountService;

    private Account account;
    private AccountRequestDto requestDto;
    private AuthResponse authResponse;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(accountService, "accountRepository", accountRepository);

        account = Account.builder()
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

        authResponse = new AuthResponse("1", "testuser", "test@example.com");
    }

    // ==================== createAccount tests ====================

    @Test
    void createAccount_validRequest_returnsAccountResponseDto() {
        when(authClient.findCustomerById(100L)).thenReturn(Optional.of(authResponse));
        when(accountRepository.existsByUserId(100L)).thenReturn(false);
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        AccountResponseDto result = accountService.createAccount(requestDto);

        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(100L);
        assertThat(result.getAccountType()).isEqualTo(Account.AccountType.SAVINGS);
        assertThat(result.getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(result.getCurrency()).isEqualTo("USD");
        assertThat(result.getIsActive()).isTrue();
        assertThat(result.getAccountNumber()).startsWith("AC");

        verify(accountRepository).save(any(Account.class));
        verify(accountEventPublisher).publishAccountCreatedEvent(
                anyString(), eq(100L), eq(Account.AccountType.SAVINGS),
                any(BigDecimal.class), eq("USD"), any(LocalDateTime.class), eq("test@example.com")
        );
    }

    @Test
    void createAccount_nullInitialDeposit_defaultsToZeroBalance() {
        AccountRequestDto requestWithNullDeposit = AccountRequestDto.builder()
                .userId(100L)
                .accountType(Account.AccountType.CHECKING)
                .currency("EUR")
                .initialDeposit(null)
                .build();

        when(authClient.findCustomerById(100L)).thenReturn(Optional.of(authResponse));
        when(accountRepository.existsByUserId(100L)).thenReturn(false);
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        AccountResponseDto result = accountService.createAccount(requestWithNullDeposit);

        assertThat(result).isNotNull();
        assertThat(result.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void createAccount_userNotFound_throwsResourceNotFoundException() {
        when(authClient.findCustomerById(999L)).thenReturn(Optional.empty());

        AccountRequestDto requestWithInvalidUser = AccountRequestDto.builder()
                .userId(999L)
                .accountType(Account.AccountType.SAVINGS)
                .currency("USD")
                .initialDeposit(new BigDecimal("500.00"))
                .build();

        assertThatThrownBy(() -> accountService.createAccount(requestWithInvalidUser))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found with id: 999");

        verify(accountRepository, never()).save(any(Account.class));
        verify(accountEventPublisher, never()).publishAccountCreatedEvent(
                anyString(), anyLong(), any(), any(), anyString(), any(), anyString()
        );
    }

    @Test
    void createAccount_userAlreadyHasAccount_throwsIllegalArgumentException() {
        when(authClient.findCustomerById(100L)).thenReturn(Optional.of(authResponse));
        when(accountRepository.existsByUserId(100L)).thenReturn(true);

        assertThatThrownBy(() -> accountService.createAccount(requestDto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User already has an account.");

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void createAccount_accountNumberCollision_regeneratesUntilUnique() {
        when(authClient.findCustomerById(100L)).thenReturn(Optional.of(authResponse));
        when(accountRepository.existsByUserId(100L)).thenReturn(false);
        // First call returns true (collision), second returns false (unique)
        when(accountRepository.existsByAccountNumber(anyString()))
                .thenReturn(true)
                .thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        AccountResponseDto result = accountService.createAccount(requestDto);

        assertThat(result).isNotNull();
        verify(accountRepository, times(2)).existsByAccountNumber(anyString());
    }

    // ==================== getAllAccounts tests ====================

    @Test
    void getAllAccounts_accountsExist_returnsListOfAccountResponseDtos() {
        Account account2 = Account.builder()
                .id(2L)
                .accountNumber("AC0987654321")
                .userId(200L)
                .accountType(Account.AccountType.CHECKING)
                .balance(new BigDecimal("2500.00"))
                .currency("EUR")
                .createdAt(LocalDateTime.of(2025, 2, 1, 12, 0))
                .isActive(true)
                .build();

        when(accountRepository.findAll()).thenReturn(List.of(account, account2));

        List<AccountResponseDto> result = accountService.getAllAccounts();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getAccountNumber()).isEqualTo("AC1234567890");
        assertThat(result.get(1).getAccountNumber()).isEqualTo("AC0987654321");
        verify(accountRepository).findAll();
    }

    @Test
    void getAllAccounts_noAccounts_returnsEmptyList() {
        when(accountRepository.findAll()).thenReturn(Collections.emptyList());

        List<AccountResponseDto> result = accountService.getAllAccounts();

        assertThat(result).isEmpty();
        verify(accountRepository).findAll();
    }

    // ==================== getAccountById tests ====================

    @Test
    void getAccountById_existingId_returnsAccountResponseDto() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        AccountResponseDto result = accountService.getAccountById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getAccountNumber()).isEqualTo("AC1234567890");
        assertThat(result.getUserId()).isEqualTo(100L);
        assertThat(result.getAccountType()).isEqualTo(Account.AccountType.SAVINGS);
        assertThat(result.getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(result.getCurrency()).isEqualTo("USD");
        assertThat(result.getIsActive()).isTrue();
        verify(accountRepository).findById(1L);
    }

    @Test
    void getAccountById_nonExistingId_throwsResourceNotFoundException() {
        when(accountRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccountById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account not found with id: 999");
    }

    // ==================== getAccountByNumber tests ====================

    @Test
    void getAccountByNumber_existingNumber_returnsAccountResponseDto() {
        when(accountRepository.findByAccountNumber("AC1234567890")).thenReturn(Optional.of(account));

        AccountResponseDto result = accountService.getAccountByNumber("AC1234567890");

        assertThat(result).isNotNull();
        assertThat(result.getAccountNumber()).isEqualTo("AC1234567890");
        verify(accountRepository).findByAccountNumber("AC1234567890");
    }

    @Test
    void getAccountByNumber_nonExistingNumber_throwsResourceNotFoundException() {
        when(accountRepository.findByAccountNumber("ACNONEXIST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccountByNumber("ACNONEXIST"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account not found with number: ACNONEXIST");
    }

    // ==================== getAccountsByUserId tests ====================

    @Test
    void getAccountsByUserId_userHasAccounts_returnsListOfAccountResponseDtos() {
        when(accountRepository.findByUserId(100L)).thenReturn(List.of(account));

        List<AccountResponseDto> result = accountService.getAccountsByUserId(100L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(100L);
        verify(accountRepository).findByUserId(100L);
    }

    @Test
    void getAccountsByUserId_userHasNoAccounts_returnsEmptyList() {
        when(accountRepository.findByUserId(999L)).thenReturn(Collections.emptyList());

        List<AccountResponseDto> result = accountService.getAccountsByUserId(999L);

        assertThat(result).isEmpty();
        verify(accountRepository).findByUserId(999L);
    }

    // ==================== getActiveAccountsByUserId tests ====================

    @Test
    void getActiveAccountsByUserId_userHasActiveAccounts_returnsActiveAccounts() {
        when(accountRepository.findByUserIdAndIsActiveTrue(100L)).thenReturn(List.of(account));

        List<AccountResponseDto> result = accountService.getActiveAccountsByUserId(100L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIsActive()).isTrue();
        verify(accountRepository).findByUserIdAndIsActiveTrue(100L);
    }

    @Test
    void getActiveAccountsByUserId_userHasNoActiveAccounts_returnsEmptyList() {
        when(accountRepository.findByUserIdAndIsActiveTrue(100L)).thenReturn(Collections.emptyList());

        List<AccountResponseDto> result = accountService.getActiveAccountsByUserId(100L);

        assertThat(result).isEmpty();
        verify(accountRepository).findByUserIdAndIsActiveTrue(100L);
    }

    // ==================== updateBalance tests ====================

    @Test
    void updateBalance_existingAccount_updatesAndReturnsAccountResponseDto() {
        BigDecimal newBalance = new BigDecimal("2000.00");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountResponseDto result = accountService.updateBalance(1L, newBalance);

        assertThat(result).isNotNull();
        assertThat(result.getBalance()).isEqualByComparingTo(newBalance);
        verify(accountRepository).findById(1L);
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    void updateBalance_existingAccount_setsUpdatedAt() {
        BigDecimal newBalance = new BigDecimal("3000.00");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        accountService.updateBalance(1L, newBalance);

        assertThat(account.getUpdatedAt()).isNotNull();
        verify(accountRepository).save(account);
    }

    @Test
    void updateBalance_nonExistingAccount_throwsResourceNotFoundException() {
        when(accountRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.updateBalance(999L, new BigDecimal("500.00")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account not found with id: 999");

        verify(accountRepository, never()).save(any(Account.class));
    }

    // ==================== deactivateAccount tests ====================

    @Test
    void deactivateAccount_existingAccount_deactivatesAndReturnsAccountResponseDto() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountResponseDto result = accountService.deactivateAccount(1L);

        assertThat(result).isNotNull();
        assertThat(result.getIsActive()).isFalse();
        verify(accountRepository).findById(1L);
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    void deactivateAccount_existingAccount_setsUpdatedAt() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        accountService.deactivateAccount(1L);

        assertThat(account.getUpdatedAt()).isNotNull();
        assertThat(account.getIsActive()).isFalse();
    }

    @Test
    void deactivateAccount_nonExistingAccount_throwsResourceNotFoundException() {
        when(accountRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.deactivateAccount(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account not found with id: 999");

        verify(accountRepository, never()).save(any(Account.class));
    }

    // ==================== mapToResponseDto verification ====================

    @Test
    void getAccountById_existingAccount_mapsAllFieldsCorrectly() {
        LocalDateTime createdAt = LocalDateTime.of(2025, 6, 15, 14, 30);
        Account fullAccount = Account.builder()
                .id(42L)
                .accountNumber("AC5555555555")
                .userId(300L)
                .accountType(Account.AccountType.CREDIT)
                .balance(new BigDecimal("99999.99"))
                .currency("GBP")
                .createdAt(createdAt)
                .isActive(false)
                .build();

        when(accountRepository.findById(42L)).thenReturn(Optional.of(fullAccount));

        AccountResponseDto result = accountService.getAccountById(42L);

        assertThat(result.getId()).isEqualTo(42L);
        assertThat(result.getAccountNumber()).isEqualTo("AC5555555555");
        assertThat(result.getUserId()).isEqualTo(300L);
        assertThat(result.getAccountType()).isEqualTo(Account.AccountType.CREDIT);
        assertThat(result.getBalance()).isEqualByComparingTo(new BigDecimal("99999.99"));
        assertThat(result.getCurrency()).isEqualTo("GBP");
        assertThat(result.getCreatedAt()).isEqualTo(createdAt);
        assertThat(result.getIsActive()).isFalse();
    }
}
