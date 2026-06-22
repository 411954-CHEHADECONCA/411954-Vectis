package com.vectis.backend.service;

import com.vectis.backend.domain.entity.Account;
import com.vectis.backend.domain.entity.Transaction;
import com.vectis.backend.domain.entity.TransactionType;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.TransferRequest;
import com.vectis.backend.dto.TransferResponse;
import com.vectis.backend.exception.VectisException;
import com.vectis.backend.repository.AccountRepository;
import com.vectis.backend.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public TransferResponse create(TransferRequest req, User user) {
        Account source = resolveAccount(req.sourceAccountId(), user);
        Account dest   = resolveAccount(req.destAccountId(),   user);

        if (source.getId().equals(dest.getId()))
            throw new VectisException("La cuenta origen y destino no pueden ser la misma", HttpStatus.BAD_REQUEST);

        BigDecimal srcAmt  = req.sourceAmount().setScale(4, RoundingMode.HALF_EVEN);
        BigDecimal destAmt = req.destAmount() != null
                ? req.destAmount().setScale(4, RoundingMode.HALF_EVEN)
                : srcAmt;

        UUID groupId = UUID.randomUUID();
        String label = req.description() != null && !req.description().isBlank()
                ? req.description().trim()
                : "Transferencia";

        Transaction debit = Transaction.builder()
                .user(user)
                .type(TransactionType.EXPENSE)
                .account(source)
                .amount(srcAmt)
                .ccy(source.getCcy())
                .description(label + " → " + dest.getName())
                .transactionDate(req.transactionDate())
                .dueDate(req.transactionDate())
                .transferGroupId(groupId)
                .build();

        Transaction credit = Transaction.builder()
                .user(user)
                .type(TransactionType.INCOME)
                .account(dest)
                .amount(destAmt)
                .ccy(dest.getCcy())
                .description(label + " ← " + source.getName())
                .transactionDate(req.transactionDate())
                .dueDate(req.transactionDate())
                .transferGroupId(groupId)
                .build();

        List<Transaction> saved = transactionRepository.saveAll(List.of(debit, credit));
        Transaction savedDebit  = saved.get(0);
        Transaction savedCredit = saved.get(1);

        return new TransferResponse(
                groupId,
                savedDebit.getId(),
                savedCredit.getId(),
                source.getId(),
                source.getName(),
                dest.getId(),
                dest.getName(),
                srcAmt,
                source.getCcy(),
                destAmt,
                dest.getCcy(),
                req.transactionDate(),
                savedDebit.getCreatedAt()
        );
    }

    @Transactional
    public void delete(UUID transferGroupId, User user) {
        int updated = transactionRepository.softDeleteByTransferGroupId(
                transferGroupId, user.getId(), OffsetDateTime.now(ZoneOffset.UTC));
        if (updated == 0)
            throw new VectisException("Transferencia no encontrada", HttpStatus.NOT_FOUND);
    }

    private Account resolveAccount(UUID id, User user) {
        return accountRepository.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> new VectisException("Cuenta no encontrada", HttpStatus.NOT_FOUND));
    }
}
