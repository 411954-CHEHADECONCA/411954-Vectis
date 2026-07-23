package com.vectis.backend.service;

import com.vectis.backend.domain.entity.Account;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("BalanceService")
class BalanceServiceTest {

    @InjectMocks
    private BalanceService balanceService;

    @Mock
    private TransactionRepository transactionRepository;

    private UUID userId;
    private Account account;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .email("user@vectis.com")
                .fullName("Test User")
                .passwordHash("hash")
                .build();

        account = Account.builder()
                .id(UUID.randomUUID())
                .user(user)
                .name("Cuenta Test")
                .kind("Banco")
                .ccy("ARS")
                .balance(new BigDecimal("100000.0000"))
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    // ─── currentBalance ───────────────────────────────────────────────────────

    @Test
    @DisplayName("sin movimientos retorna el saldo inicial")
    void currentBalance_noMovements_returnsOpeningBalance() {
        given(transactionRepository.netMovementsForAccount(userId, account.getId(), LocalDate.now()))
                .willReturn(BigDecimal.ZERO);

        BigDecimal result = balanceService.currentBalance(account, userId);

        assertThat(result).isEqualByComparingTo("100000.0000");
    }

    @Test
    @DisplayName("con ingresos suma al saldo inicial")
    void currentBalance_withIncome_addsToOpeningBalance() {
        given(transactionRepository.netMovementsForAccount(userId, account.getId(), LocalDate.now()))
                .willReturn(new BigDecimal("13500.0000"));

        BigDecimal result = balanceService.currentBalance(account, userId);

        assertThat(result).isEqualByComparingTo("113500.0000");
    }

    @Test
    @DisplayName("con gastos resta del saldo inicial")
    void currentBalance_withExpenses_subtractsFromOpeningBalance() {
        given(transactionRepository.netMovementsForAccount(userId, account.getId(), LocalDate.now()))
                .willReturn(new BigDecimal("-25000.0000"));

        BigDecimal result = balanceService.currentBalance(account, userId);

        assertThat(result).isEqualByComparingTo("75000.0000");
    }

    @Test
    @DisplayName("combinacion ingreso + gasto aplica neto con HALF_EVEN")
    void currentBalance_netMovements_appliesHalfEvenRounding() {
        given(transactionRepository.netMovementsForAccount(userId, account.getId(), LocalDate.now()))
                .willReturn(new BigDecimal("333.3333"));

        BigDecimal result = balanceService.currentBalance(account, userId);

        assertThat(result).isEqualByComparingTo("100333.3333");
        assertThat(result.scale()).isEqualTo(4);
    }

    // ─── balanceAtDate ────────────────────────────────────────────────────────

    @Test
    @DisplayName("balanceAtDate usa la fecha indicada como upTo")
    void balanceAtDate_usesProvidedDate() {
        LocalDate targetDate = LocalDate.of(2025, 1, 31);

        given(transactionRepository.netMovementsForAccount(userId, account.getId(), targetDate))
                .willReturn(new BigDecimal("5000.0000"));

        BigDecimal result = balanceService.balanceAtDate(account, userId, targetDate);

        assertThat(result).isEqualByComparingTo("105000.0000");
    }

    @Test
    @DisplayName("balanceAtDate con fecha futura retorna saldo inicial cuando no hay movimientos")
    void balanceAtDate_futureDate_returnsOpeningWhenNoMovements() {
        LocalDate futureDate = LocalDate.now().plusMonths(3);

        given(transactionRepository.netMovementsForAccount(userId, account.getId(), futureDate))
                .willReturn(BigDecimal.ZERO);

        BigDecimal result = balanceService.balanceAtDate(account, userId, futureDate);

        assertThat(result).isEqualByComparingTo("100000.0000");
    }

    @Test
    @DisplayName("resultado siempre tiene scale 4")
    void currentBalance_resultHasScale4() {
        given(transactionRepository.netMovementsForAccount(userId, account.getId(), LocalDate.now()))
                .willReturn(BigDecimal.ZERO);

        BigDecimal result = balanceService.currentBalance(account, userId);

        assertThat(result.scale()).isEqualTo(4);
    }
}
