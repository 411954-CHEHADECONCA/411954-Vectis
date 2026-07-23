package com.vectis.backend.service;

import com.vectis.backend.domain.entity.Account;
import com.vectis.backend.domain.entity.InvestmentAsset;
import com.vectis.backend.domain.entity.InvestmentAssetStatus;
import com.vectis.backend.domain.entity.InvestmentAssetType;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.AccountBalanceResponse;
import com.vectis.backend.dto.AccountRequest;
import com.vectis.backend.dto.AccountResponse;
import com.vectis.backend.exception.AccountNotFoundException;
import com.vectis.backend.exception.VectisException;
import com.vectis.backend.mapper.AccountMapper;
import com.vectis.backend.repository.AccountRepository;
import com.vectis.backend.repository.InvestmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService")
class AccountServiceTest {

    @InjectMocks
    private AccountService accountService;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private BalanceService balanceService;

    @Mock
    private InvestmentRepository investmentRepository;

    private User user;
    private User otherUser;
    private UUID userId;
    private UUID otherId;

    @BeforeEach
    void setUp() {
        userId  = UUID.randomUUID();
        otherId = UUID.randomUUID();

        user = User.builder()
                .id(userId)
                .email("user@vectis.com")
                .fullName("Test User")
                .passwordHash("hash")
                .build();

        otherUser = User.builder()
                .id(otherId)
                .email("other@vectis.com")
                .fullName("Other User")
                .passwordHash("hash")
                .build();
    }

    // ─── getAccounts ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getAccounts devuelve solo las cuentas del usuario con computedBalance")
    void getAccounts_returnsOnlyUserAccountsWithComputedBalance() {
        Account account = buildAccount(user);
        AccountResponse baseResponse = buildResponse(account, null, null);

        given(accountRepository.findAllByUser_IdOrderByCreatedAtAsc(userId)).willReturn(List.of(account));
        given(investmentRepository.findFciLinksByUser(userId)).willReturn(List.of());
        given(accountMapper.toResponse(account, null)).willReturn(baseResponse);
        given(balanceService.currentBalance(account, userId)).willReturn(new BigDecimal("163500.0000"));

        List<AccountResponse> result = accountService.getAccounts(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).computedBalance()).isEqualByComparingTo("163500.0000");
        verify(accountRepository).findAllByUser_IdOrderByCreatedAtAsc(userId);
        verify(balanceService).currentBalance(account, userId);
    }

    @Test
    @DisplayName("getAccounts marca remunerada=true y usa la TNA cuando hay un FCI vinculado")
    void getAccounts_withLinkedFci_derivesRemuneradaAndTna() {
        Account account = buildAccount(user);
        BigDecimal linkedTna = new BigDecimal("81.0000");
        AccountResponse baseResponse = buildResponse(account, null, linkedTna);

        given(accountRepository.findAllByUser_IdOrderByCreatedAtAsc(userId)).willReturn(List.of(account));
        given(investmentRepository.findFciLinksByUser(userId))
                .willReturn(List.of(fciLink(account.getId(), linkedTna, OffsetDateTime.now())));
        given(accountMapper.toResponse(account, linkedTna)).willReturn(baseResponse);
        given(balanceService.currentBalance(account, userId)).willReturn(new BigDecimal("150000.0000"));

        List<AccountResponse> result = accountService.getAccounts(userId);

        assertThat(result.get(0).remunerada()).isTrue();
        assertThat(result.get(0).tna()).isEqualByComparingTo(linkedTna);
    }

    @Test
    @DisplayName("getAccounts usa la TNA del FCI más reciente cuando hay varios vinculados a la misma cuenta")
    void getAccounts_withMultipleLinkedFci_usesMostRecent() {
        Account account = buildAccount(user);
        BigDecimal olderTna = new BigDecimal("50.0000");
        BigDecimal newerTna = new BigDecimal("81.0000");
        AccountResponse baseResponse = buildResponse(account, null, newerTna);

        given(accountRepository.findAllByUser_IdOrderByCreatedAtAsc(userId)).willReturn(List.of(account));
        given(investmentRepository.findFciLinksByUser(userId)).willReturn(List.of(
                fciLink(account.getId(), olderTna, OffsetDateTime.now().minusDays(10)),
                fciLink(account.getId(), newerTna, OffsetDateTime.now())
        ));
        given(accountMapper.toResponse(account, newerTna)).willReturn(baseResponse);
        given(balanceService.currentBalance(account, userId)).willReturn(new BigDecimal("150000.0000"));

        List<AccountResponse> result = accountService.getAccounts(userId);

        assertThat(result.get(0).tna()).isEqualByComparingTo(newerTna);
    }

    private InvestmentRepository.FciAccountLinkView fciLink(UUID accountId, BigDecimal tna, OffsetDateTime createdAt) {
        return new InvestmentRepository.FciAccountLinkView() {
            @Override public UUID getAccountId() { return accountId; }
            @Override public BigDecimal getTna() { return tna; }
            @Override public OffsetDateTime getCreatedAt() { return createdAt; }
        };
    }

    // ─── getBalance ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("getBalance retorna el saldo calculado a la fecha indicada")
    void getBalance_returnsComputedBalanceAtDate() {
        UUID id = UUID.randomUUID();
        Account account = buildAccount(user);
        LocalDate asOf = LocalDate.of(2025, 1, 31);

        given(accountRepository.findById(id)).willReturn(Optional.of(account));
        given(balanceService.balanceAtDate(account, userId, asOf)).willReturn(new BigDecimal("113500.0000"));

        AccountBalanceResponse result = accountService.getBalance(id, user, asOf);

        assertThat(result.computedBalance()).isEqualByComparingTo("113500.0000");
        assertThat(result.openingBalance()).isEqualByComparingTo("150000.0000");
        assertThat(result.asOf()).isEqualTo(asOf);
    }

    @Test
    @DisplayName("getBalance de cuenta inexistente lanza NOT_FOUND")
    void getBalance_notFound_throwsAccountNotFoundException() {
        UUID id = UUID.randomUUID();
        given(accountRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getBalance(id, user, LocalDate.now()))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("getBalance de cuenta de otro usuario lanza FORBIDDEN")
    void getBalance_otherUserAccount_throwsForbidden() {
        UUID id = UUID.randomUUID();
        Account otherAccount = buildAccount(otherUser);
        given(accountRepository.findById(id)).willReturn(Optional.of(otherAccount));

        assertThatThrownBy(() -> accountService.getBalance(id, user, LocalDate.now()))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    // ─── createAccount ────────────────────────────────────────────────────────

    @Test
    @DisplayName("createAccount persiste con el usuario autenticado y retorna computedBalance")
    void createAccount_persistsWithAuthenticatedUser() {
        AccountRequest request = buildRequest();
        Account saved = buildAccount(user);
        AccountResponse baseResponse = buildResponse(saved, null, null);

        given(accountRepository.save(any(Account.class))).willReturn(saved);
        given(investmentRepository.findTopByAccount_IdAndTypeAndStatusOrderByCreatedAtDesc(saved.getId(), InvestmentAssetType.FCI, InvestmentAssetStatus.ACTIVA))
                .willReturn(Optional.empty());
        given(accountMapper.toResponse(saved, null)).willReturn(baseResponse);
        given(balanceService.currentBalance(saved, userId)).willReturn(new BigDecimal("150000.0000"));

        AccountResponse result = accountService.createAccount(request, user);

        assertThat(result.name()).isEqualTo("Cuenta Test");
        assertThat(result.computedBalance()).isEqualByComparingTo("150000.0000");
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    @DisplayName("createAccount de una cuenta recién creada nunca tiene FCI vinculado")
    void createAccount_freshAccount_hasNoDerivedTna() {
        AccountRequest request = buildRequest();
        Account saved = buildAccount(user);
        AccountResponse baseResponse = buildResponse(saved, null, null);

        given(accountRepository.save(any(Account.class))).willReturn(saved);
        given(investmentRepository.findTopByAccount_IdAndTypeAndStatusOrderByCreatedAtDesc(saved.getId(), InvestmentAssetType.FCI, InvestmentAssetStatus.ACTIVA))
                .willReturn(Optional.empty());
        given(accountMapper.toResponse(saved, null)).willReturn(baseResponse);
        given(balanceService.currentBalance(eq(saved), eq(userId))).willReturn(new BigDecimal("150000.0000"));

        AccountResponse result = accountService.createAccount(request, user);

        assertThat(result.remunerada()).isFalse();
        assertThat(result.tna()).isNull();
    }

    // ─── updateAccount ────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateAccount de cuenta inexistente lanza NOT_FOUND")
    void updateAccount_notFound_throwsAccountNotFoundException() {
        UUID id = UUID.randomUUID();
        given(accountRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.updateAccount(id, buildRequest(), user))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("updateAccount de cuenta de otro usuario lanza FORBIDDEN")
    void updateAccount_otherUserAccount_throwsForbidden() {
        UUID id = UUID.randomUUID();
        Account otherAccount = buildAccount(otherUser);
        given(accountRepository.findById(id)).willReturn(Optional.of(otherAccount));

        assertThatThrownBy(() -> accountService.updateAccount(id, buildRequest(), user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("updateAccount propia actualiza todos los campos y retorna computedBalance")
    void updateAccount_ownAccount_updatesAllFields() {
        UUID id = UUID.randomUUID();
        Account account = buildAccount(user);
        AccountRequest request = new AccountRequest(
                "Cuenta Actualizada", "Banco", "Caja Ahorro $", "USD",
                new BigDecimal("200000.0000"), true);
        AccountResponse baseResponse = buildResponse(account, null, null);

        given(accountRepository.findById(id)).willReturn(Optional.of(account));
        given(accountRepository.save(account)).willReturn(account);
        given(investmentRepository.findTopByAccount_IdAndTypeAndStatusOrderByCreatedAtDesc(account.getId(), InvestmentAssetType.FCI, InvestmentAssetStatus.ACTIVA))
                .willReturn(Optional.empty());
        given(accountMapper.toResponse(account, null)).willReturn(baseResponse);
        given(balanceService.currentBalance(account, userId)).willReturn(new BigDecimal("200000.0000"));

        AccountResponse result = accountService.updateAccount(id, request, user);

        assertThat(result).isNotNull();
        assertThat(result.computedBalance()).isEqualByComparingTo("200000.0000");
        verify(accountRepository).save(account);
    }

    @Test
    @DisplayName("updateAccount refleja la TNA del FCI ya vinculado a la cuenta")
    void updateAccount_withLinkedFci_derivesTna() {
        UUID id = UUID.randomUUID();
        Account account = buildAccount(user);
        BigDecimal linkedTna = new BigDecimal("81.0000");
        AccountRequest request = buildRequest();
        AccountResponse baseResponse = buildResponse(account, null, linkedTna);
        InvestmentAsset linkedFci = InvestmentAsset.builder().tna(linkedTna).build();

        given(accountRepository.findById(id)).willReturn(Optional.of(account));
        given(accountRepository.save(account)).willReturn(account);
        given(investmentRepository.findTopByAccount_IdAndTypeAndStatusOrderByCreatedAtDesc(account.getId(), InvestmentAssetType.FCI, InvestmentAssetStatus.ACTIVA))
                .willReturn(Optional.of(linkedFci));
        given(accountMapper.toResponse(account, linkedTna)).willReturn(baseResponse);
        given(balanceService.currentBalance(account, userId)).willReturn(new BigDecimal("150000.0000"));

        AccountResponse result = accountService.updateAccount(id, request, user);

        assertThat(result.remunerada()).isTrue();
        assertThat(result.tna()).isEqualByComparingTo(linkedTna);
    }

    // ─── deleteAccount ────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteAccount de cuenta inexistente lanza NOT_FOUND")
    void deleteAccount_notFound_throwsAccountNotFoundException() {
        UUID id = UUID.randomUUID();
        given(accountRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.deleteAccount(id, user))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("deleteAccount de cuenta de otro usuario lanza FORBIDDEN")
    void deleteAccount_otherUserAccount_throwsForbidden() {
        UUID id = UUID.randomUUID();
        Account otherAccount = buildAccount(otherUser);
        given(accountRepository.findById(id)).willReturn(Optional.of(otherAccount));

        assertThatThrownBy(() -> accountService.deleteAccount(id, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("deleteAccount propia elimina la cuenta")
    void deleteAccount_ownAccount_deletesSuccessfully() {
        UUID id = UUID.randomUUID();
        Account account = buildAccount(user);
        given(accountRepository.findById(id)).willReturn(Optional.of(account));

        accountService.deleteAccount(id, user);

        verify(accountRepository).delete(account);
    }

    // ─── includeInCashflow ────────────────────────────────────────────────────

    @Test
    @DisplayName("createAccount con includeInCashflow=false persiste el flag en false")
    void createAccount_includeInCashflowFalse_persistsFlag() {
        AccountRequest request = new AccountRequest(
                "Cuenta Test", "Banco", "Caja de Ahorro $", "ARS",
                new BigDecimal("150000.0000"), false);

        Account saved = Account.builder()
                .id(UUID.randomUUID()).user(user).name("Cuenta Test").kind("Banco")
                .detail("Caja de Ahorro $").ccy("ARS").balance(new BigDecimal("150000.0000"))
                .includeInCashflow(false)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();
        AccountResponse baseResponse = buildResponse(saved, null, null);

        given(accountRepository.save(any(Account.class))).willReturn(saved);
        given(investmentRepository.findTopByAccount_IdAndTypeAndStatusOrderByCreatedAtDesc(saved.getId(), InvestmentAssetType.FCI, InvestmentAssetStatus.ACTIVA))
                .willReturn(Optional.empty());
        given(accountMapper.toResponse(saved, null)).willReturn(baseResponse);
        given(balanceService.currentBalance(saved, userId)).willReturn(new BigDecimal("150000.0000"));

        AccountResponse result = accountService.createAccount(request, user);

        assertThat(result.includeInCashflow()).isFalse();
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    @DisplayName("updateAccount persiste includeInCashflow=false cuando se actualiza")
    void updateAccount_includeInCashflowFalse_persistsFlag() {
        UUID id = UUID.randomUUID();
        Account account = buildAccount(user);
        AccountRequest request = new AccountRequest(
                "Cuenta Test", "Banco", "Caja de Ahorro $", "ARS",
                new BigDecimal("150000.0000"), false);

        Account updatedAccount = Account.builder()
                .id(id).user(user).name("Cuenta Test").kind("Banco")
                .detail("Caja de Ahorro $").ccy("ARS").balance(new BigDecimal("150000.0000"))
                .includeInCashflow(false)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();
        AccountResponse baseResponse = buildResponse(updatedAccount, null, null);

        given(accountRepository.findById(id)).willReturn(Optional.of(account));
        given(accountRepository.save(account)).willReturn(updatedAccount);
        given(investmentRepository.findTopByAccount_IdAndTypeAndStatusOrderByCreatedAtDesc(updatedAccount.getId(), InvestmentAssetType.FCI, InvestmentAssetStatus.ACTIVA))
                .willReturn(Optional.empty());
        given(accountMapper.toResponse(updatedAccount, null)).willReturn(baseResponse);
        given(balanceService.currentBalance(updatedAccount, userId)).willReturn(new BigDecimal("150000.0000"));

        AccountResponse result = accountService.updateAccount(id, request, user);

        assertThat(result.includeInCashflow()).isFalse();
        verify(accountRepository).save(account);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Account buildAccount(User owner) {
        return Account.builder()
                .id(UUID.randomUUID())
                .user(owner)
                .name("Cuenta Test")
                .kind("Banco")
                .detail("Caja de Ahorro $")
                .ccy("ARS")
                .balance(new BigDecimal("150000.0000"))
                .includeInCashflow(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    private AccountRequest buildRequest() {
        return new AccountRequest(
                "Cuenta Test", "Banco", "Caja de Ahorro $", "ARS",
                new BigDecimal("150000.0000"), true);
    }

    private AccountResponse buildResponse(Account a, BigDecimal computedBalance, BigDecimal derivedTna) {
        return new AccountResponse(
                a.getId(), a.getName(), a.getKind(), a.getDetail(), a.getCcy(),
                a.getBalance(), computedBalance,
                derivedTna != null, derivedTna, a.isIncludeInCashflow(),
                a.getCreatedAt(), a.getUpdatedAt());
    }
}
