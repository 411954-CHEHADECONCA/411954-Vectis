package com.vectis.backend.service;

import com.vectis.backend.domain.entity.Account;
import com.vectis.backend.domain.entity.Category;
import com.vectis.backend.domain.entity.RecurringMovement;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.RecurringMovementRequest;
import com.vectis.backend.dto.RecurringMovementResponse;
import com.vectis.backend.exception.AccountNotFoundException;
import com.vectis.backend.exception.RecurringMovementNotFoundException;
import com.vectis.backend.exception.VectisException;
import com.vectis.backend.mapper.RecurringMovementMapper;
import com.vectis.backend.repository.AccountRepository;
import com.vectis.backend.repository.CategoryRepository;
import com.vectis.backend.repository.RecurringMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class RecurringMovementService {

    private final RecurringMovementRepository recurringMovementRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final RecurringMovementMapper recurringMovementMapper;

    @Transactional(readOnly = true)
    public List<RecurringMovementResponse> getRecurringMovements(UUID userId) {
        return recurringMovementRepository
                .findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(userId)
                .stream()
                .map(recurringMovementMapper::toResponse)
                .toList();
    }

    public RecurringMovementResponse createRecurringMovement(RecurringMovementRequest request, User user) {
        Category category = resolveCategory(request.categoryId(), user);
        Account account = resolveAccount(request.accountId(), user);

        RecurringMovement rm = RecurringMovement.builder()
                .user(user)
                .description(request.description())
                .amount(request.amount())
                .ccy(request.ccy())
                .type(request.type())
                .category(category)
                .account(account)
                .dayOfMonth(request.dayOfMonth())
                .build();

        return recurringMovementMapper.toResponse(recurringMovementRepository.save(rm));
    }

    public RecurringMovementResponse updateRecurringMovement(UUID id, RecurringMovementRequest request, User user) {
        RecurringMovement rm = recurringMovementRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RecurringMovementNotFoundException(id));

        if (!rm.getUser().getId().equals(user.getId())) {
            throw new VectisException("No tenés permiso para modificar este movimiento recurrente", HttpStatus.FORBIDDEN);
        }

        Category category = resolveCategory(request.categoryId(), user);
        Account account = resolveAccount(request.accountId(), user);

        rm.setDescription(request.description());
        rm.setAmount(request.amount());
        rm.setCcy(request.ccy());
        rm.setType(request.type());
        rm.setCategory(category);
        rm.setAccount(account);
        rm.setDayOfMonth(request.dayOfMonth());

        return recurringMovementMapper.toResponse(recurringMovementRepository.save(rm));
    }

    public RecurringMovementResponse toggleActive(UUID id, User user) {
        RecurringMovement rm = recurringMovementRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RecurringMovementNotFoundException(id));

        if (!rm.getUser().getId().equals(user.getId())) {
            throw new VectisException("No tenés permiso para modificar este movimiento recurrente", HttpStatus.FORBIDDEN);
        }

        rm.setActive(!rm.isActive());
        return recurringMovementMapper.toResponse(recurringMovementRepository.save(rm));
    }

    public void deleteRecurringMovement(UUID id, User user) {
        RecurringMovement rm = recurringMovementRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RecurringMovementNotFoundException(id));

        if (!rm.getUser().getId().equals(user.getId())) {
            throw new VectisException("No tenés permiso para eliminar este movimiento recurrente", HttpStatus.FORBIDDEN);
        }

        rm.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        recurringMovementRepository.save(rm);
    }

    private Category resolveCategory(UUID categoryId, User user) {
        if (categoryId == null) return null;
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new VectisException("Categoría no encontrada: " + categoryId, HttpStatus.NOT_FOUND));
    }

    private Account resolveAccount(UUID accountId, User user) {
        if (accountId == null) return null;
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        if (!account.getUser().getId().equals(user.getId())) {
            throw new VectisException("La cuenta no pertenece al usuario autenticado", HttpStatus.FORBIDDEN);
        }
        return account;
    }
}
