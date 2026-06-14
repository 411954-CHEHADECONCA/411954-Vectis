package com.vectis.backend.service;

import com.vectis.backend.domain.entity.Account;
import com.vectis.backend.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BalanceService {

    private final TransactionRepository transactionRepository;

    public BigDecimal currentBalance(Account account, UUID userId) {
        return balanceAtDate(account, userId, LocalDate.now());
    }

    public BigDecimal balanceAtDate(Account account, UUID userId, LocalDate date) {
        BigDecimal net = transactionRepository
                .netMovementsForAccount(userId, account.getId(), date);
        return account.getBalance()
                .add(net)
                .setScale(4, RoundingMode.HALF_EVEN);
    }
}
