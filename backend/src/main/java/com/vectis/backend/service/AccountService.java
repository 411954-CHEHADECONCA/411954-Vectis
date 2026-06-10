package com.vectis.backend.service;

import com.vectis.backend.domain.entity.Account;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.AccountRequest;
import com.vectis.backend.dto.AccountResponse;
import com.vectis.backend.exception.AccountNotFoundException;
import com.vectis.backend.exception.VectisException;
import com.vectis.backend.mapper.AccountMapper;
import com.vectis.backend.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;

    @Transactional(readOnly = true)
    public List<AccountResponse> getAccounts(UUID userId) {
        return accountRepository.findAllByUser_IdOrderByCreatedAtAsc(userId)
                .stream()
                .map(accountMapper::toResponse)
                .toList();
    }

    public AccountResponse createAccount(AccountRequest request, User user) {
        Account account = Account.builder()
                .user(user)
                .name(request.name())
                .kind(request.kind())
                .detail(request.detail())
                .ccy(request.ccy())
                .balance(request.balance())
                .remunerada(Boolean.TRUE.equals(request.remunerada()))
                .tna(request.tna())
                .build();

        return accountMapper.toResponse(accountRepository.save(account));
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
        account.setRemunerada(Boolean.TRUE.equals(request.remunerada()));
        account.setTna(request.tna());

        return accountMapper.toResponse(accountRepository.save(account));
    }

    public void deleteAccount(UUID id, User user) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));

        if (!account.getUser().getId().equals(user.getId())) {
            throw new VectisException("No tenés permiso para eliminar esta cuenta", HttpStatus.FORBIDDEN);
        }

        accountRepository.delete(account);
    }
}
