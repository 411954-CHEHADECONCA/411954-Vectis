package com.vectis.backend.service;

import com.vectis.backend.domain.entity.Account;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.AccountRequest;
import com.vectis.backend.dto.AccountResponse;
import com.vectis.backend.exception.AccountNotFoundException;
import com.vectis.backend.exception.VectisException;
import com.vectis.backend.mapper.AccountMapper;
import com.vectis.backend.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    @DisplayName("getAccounts devuelve solo las cuentas del usuario")
    void getAccounts_returnsOnlyUserAccounts() {
        Account account = buildAccount(user);
        AccountResponse response = buildResponse(account);

        given(accountRepository.findAllByUser_IdOrderByCreatedAtAsc(userId)).willReturn(List.of(account));
        given(accountMapper.toResponse(account)).willReturn(response);

        List<AccountResponse> result = accountService.getAccounts(userId);

        assertThat(result).hasSize(1);
        verify(accountRepository).findAllByUser_IdOrderByCreatedAtAsc(userId);
    }

    // ─── createAccount ────────────────────────────────────────────────────────

    @Test
    @DisplayName("createAccount persiste con el usuario autenticado")
    void createAccount_persistsWithAuthenticatedUser() {
        AccountRequest request = buildRequest(false, null);
        Account saved = buildAccount(user);
        AccountResponse response = buildResponse(saved);

        given(accountRepository.save(any(Account.class))).willReturn(saved);
        given(accountMapper.toResponse(saved)).willReturn(response);

        AccountResponse result = accountService.createAccount(request, user);

        assertThat(result.name()).isEqualTo("Cuenta Test");
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    @DisplayName("createAccount no remunerada acepta TNA nula")
    void createAccount_nonRemunerada_tnaNullAllowed() {
        AccountRequest request = buildRequest(false, null);
        Account saved = buildAccount(user);
        AccountResponse response = buildResponse(saved);

        given(accountRepository.save(any(Account.class))).willReturn(saved);
        given(accountMapper.toResponse(saved)).willReturn(response);

        AccountResponse result = accountService.createAccount(request, user);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("createAccount remunerada persiste la TNA")
    void createAccount_remunerada_persistsTna() {
        AccountRequest request = buildRequest(true, new BigDecimal("81.0000"));
        Account saved = buildAccount(user);
        AccountResponse response = buildResponse(saved);

        given(accountRepository.save(any(Account.class))).willReturn(saved);
        given(accountMapper.toResponse(saved)).willReturn(response);

        AccountResponse result = accountService.createAccount(request, user);

        assertThat(result).isNotNull();
        verify(accountRepository).save(any(Account.class));
    }

    // ─── updateAccount ────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateAccount de cuenta inexistente lanza NOT_FOUND")
    void updateAccount_notFound_throwsAccountNotFoundException() {
        UUID id = UUID.randomUUID();
        given(accountRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.updateAccount(id, buildRequest(false, null), user))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("updateAccount de cuenta de otro usuario lanza FORBIDDEN")
    void updateAccount_otherUserAccount_throwsForbidden() {
        UUID id = UUID.randomUUID();
        Account otherAccount = buildAccount(otherUser);
        given(accountRepository.findById(id)).willReturn(Optional.of(otherAccount));

        assertThatThrownBy(() -> accountService.updateAccount(id, buildRequest(false, null), user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("updateAccount propia actualiza todos los campos")
    void updateAccount_ownAccount_updatesAllFields() {
        UUID id = UUID.randomUUID();
        Account account = buildAccount(user);
        AccountRequest request = new AccountRequest(
                "Cuenta Actualizada", "Banco", "Caja Ahorro $", "USD",
                new BigDecimal("200000.0000"), true, new BigDecimal("95.0000"));
        AccountResponse response = buildResponse(account);

        given(accountRepository.findById(id)).willReturn(Optional.of(account));
        given(accountRepository.save(account)).willReturn(account);
        given(accountMapper.toResponse(account)).willReturn(response);

        AccountResponse result = accountService.updateAccount(id, request, user);

        assertThat(result).isNotNull();
        verify(accountRepository).save(account);
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
                .remunerada(false)
                .tna(null)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    private AccountRequest buildRequest(boolean remunerada, BigDecimal tna) {
        return new AccountRequest(
                "Cuenta Test", "Banco", "Caja de Ahorro $", "ARS",
                new BigDecimal("150000.0000"), remunerada, tna);
    }

    private AccountResponse buildResponse(Account a) {
        return new AccountResponse(
                a.getId(), a.getName(), a.getKind(), a.getDetail(), a.getCcy(),
                a.getBalance(), a.isRemunerada(), a.getTna(), a.getCreatedAt(), a.getUpdatedAt());
    }
}
