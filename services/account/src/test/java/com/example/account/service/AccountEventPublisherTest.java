package com.example.account.service;

import com.example.account.event.AccountEvent;
import com.example.account.model.Account;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccountEventPublisherTest {

    @Mock
    private KafkaTemplate<String, AccountEvent> kafkaTemplate;

    @InjectMocks
    private AccountEventPublisher accountEventPublisher;

    @Captor
    private ArgumentCaptor<AccountEvent> eventCaptor;

    @Test
    void publishAccountCreatedEvent_validInput_sendsEventToKafkaTopic() {
        String accountNumber = "AC1234567890";
        Long userId = 100L;
        Account.AccountType accountType = Account.AccountType.SAVINGS;
        BigDecimal balance = new BigDecimal("1000.00");
        String currency = "USD";
        LocalDateTime createdAt = LocalDateTime.of(2025, 1, 1, 10, 0);
        String email = "test@example.com";

        accountEventPublisher.publishAccountCreatedEvent(
                accountNumber, userId, accountType, balance, currency, createdAt, email
        );

        verify(kafkaTemplate).send(eq("account-events"), eventCaptor.capture());

        AccountEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getEventType()).isEqualTo("ACCOUNT_CREATED");
        assertThat(capturedEvent.getAccountNumber()).isEqualTo(accountNumber);
        assertThat(capturedEvent.getUserId()).isEqualTo(userId);
        assertThat(capturedEvent.getAccountType()).isEqualTo("SAVINGS");
        assertThat(capturedEvent.getBalance()).isEqualByComparingTo(balance);
        assertThat(capturedEvent.getCurrency()).isEqualTo(currency);
        assertThat(capturedEvent.getCreatedAt()).isEqualTo(createdAt);
        assertThat(capturedEvent.getEmail()).isEqualTo(email);
    }

    @Test
    void publishAccountCreatedEvent_checkingAccountType_setsCorrectAccountType() {
        accountEventPublisher.publishAccountCreatedEvent(
                "AC9999999999", 200L, Account.AccountType.CHECKING,
                new BigDecimal("500.00"), "EUR",
                LocalDateTime.of(2025, 6, 15, 14, 30), "user@test.com"
        );

        verify(kafkaTemplate).send(eq("account-events"), eventCaptor.capture());

        AccountEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getAccountType()).isEqualTo("CHECKING");
    }

    @Test
    void publishAccountCreatedEvent_creditAccountType_setsCorrectAccountType() {
        accountEventPublisher.publishAccountCreatedEvent(
                "AC8888888888", 300L, Account.AccountType.CREDIT,
                BigDecimal.ZERO, "GBP",
                LocalDateTime.now(), "credit@test.com"
        );

        verify(kafkaTemplate).send(eq("account-events"), eventCaptor.capture());

        AccountEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getAccountType()).isEqualTo("CREDIT");
    }

    @Test
    void publishAccountCreatedEvent_zeroBalance_sendsEventWithZeroBalance() {
        accountEventPublisher.publishAccountCreatedEvent(
                "AC7777777777", 400L, Account.AccountType.SAVINGS,
                BigDecimal.ZERO, "USD",
                LocalDateTime.now(), "zero@test.com"
        );

        verify(kafkaTemplate).send(eq("account-events"), eventCaptor.capture());

        AccountEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
