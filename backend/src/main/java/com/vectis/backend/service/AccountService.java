package com.vectis.backend.service;

import com.vectis.backend.domain.entity.Account;
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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final BalanceService balanceService;
    private final InvestmentRepository investmentRepository;

    @Transactional(readOnly = true)
    public List<AccountResponse> getAccounts(UUID userId) {
        Map<UUID, BigDecimal> tnaByAccountId = investmentRepository.findFciLinksByUser(userId).stream()
                .collect(Collectors.toMap(
                        InvestmentRepository.FciAccountLinkView::getAccountId,
                        link -> link,
                        BinaryOperator.maxBy(Comparator.comparing(InvestmentRepository.FciAccountLinkView::getCreatedAt))
                ))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getTna()));

        return accountRepository.findAllByUser_IdOrderByCreatedAtAsc(userId)
                .stream()
                .map(a -> withComputedBalance(a, userId, tnaByAccountId.get(a.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountBalanceResponse getBalance(UUID accountId, User user, LocalDate asOf) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        if (!account.getUser().getId().equals(user.getId())) {
            throw new VectisException("No tenés permiso para consultar esta cuenta", HttpStatus.FORBIDDEN);
        }

        BigDecimal computed = balanceService.balanceAtDate(account, user.getId(), asOf);
        return new AccountBalanceResponse(
                account.getId(),
                account.getName(),
                account.getCcy(),
                account.getBalance(),
                computed,
                asOf
        );
    }

    public AccountResponse createAccount(AccountRequest request, User user) {
        Account account = Account.builder()
                .user(user)
                .name(request.name())
                .kind(request.kind())
                .detail(request.detail())
                .ccy(request.ccy())
                .balance(request.balance())
                .includeInCashflow(request.includeInCashflow())
                .build();

        Account saved = accountRepository.save(account);
        return withComputedBalance(saved, user.getId(), derivedTna(saved.getId()));
    }

    public AccountResponse updateAccount(UUID id, AccountRequest request, User user) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));

        if (!account.getUser().getId().equals(user.getId())) {
            throw new VectisException("No tenés permiso para modificar esta cuenta", HttpStatus.FORBIDDEN);
        }

        account.setName(request.name());
        account.setKind(request.kind());
        account.setDetail(request.detail());
        account.setCcy(request.ccy());
        account.setBalance(request.balance());
        account.setIncludeInCashflow(request.includeInCashflow());

        Account saved = accountRepository.save(account);
        return withComputedBalance(saved, user.getId(), derivedTna(saved.getId()));
    }

    private BigDecimal derivedTna(UUID accountId) {
        return investmentRepository.findTopByAccount_IdAndTypeOrderByCreatedAtDesc(accountId, InvestmentAssetType.FCI)
                .map(com.vectis.backend.domain.entity.InvestmentAsset::getTna)
                .orElse(null);
    }

    public void deleteAccount(UUID id, User user) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));

        if (!account.getUser().getId().equals(user.getId())) {
            throw new VectisException("No tenés permiso para eliminar esta cuenta", HttpStatus.FORBIDDEN);
        }

        accountRepository.delete(account);
    }

    private AccountResponse withComputedBalance(Account account, UUID userId, BigDecimal derivedTna) {
        AccountResponse base = accountMapper.toResponse(account, derivedTna);
        BigDecimal computed = balanceService.currentBalance(account, userId);
        return AccountResponse.builder()
                .id(base.id())
                .name(base.name())
                .kind(base.kind())
                .detail(base.detail())
                .ccy(base.ccy())
                .balance(base.balance())
                .computedBalance(computed)
                .remunerada(base.remunerada())
                .tna(base.tna())
                .includeInCashflow(base.includeInCashflow())
                .createdAt(base.createdAt())
                .updatedAt(base.updatedAt())
                .build();
    }
}
