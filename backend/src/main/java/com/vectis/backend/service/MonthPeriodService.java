package com.vectis.backend.service;

import com.vectis.backend.domain.entity.CategoryBudget;
import com.vectis.backend.domain.entity.CategoryType;
import com.vectis.backend.domain.entity.MonthPeriod;
import com.vectis.backend.domain.entity.RecurringMovement;
import com.vectis.backend.domain.entity.Transaction;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.repository.CategoryBudgetRepository;
import com.vectis.backend.repository.MonthPeriodRepository;
import com.vectis.backend.repository.RecurringMovementRepository;
import com.vectis.backend.repository.TransactionRepository;
import com.vectis.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonthPeriodService {

    private final MonthPeriodRepository monthPeriodRepository;
    private final RecurringMovementRepository recurringMovementRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final CategoryBudgetRepository categoryBudgetRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Determines the display status of a month for a given user.
     *
     * <ul>
     *   <li>Future month (no record or PROJECTED record) → "proyectado"</li>
     *   <li>Row exists + CLOSED → "cerrado"</li>
     *   <li>Row exists + OPEN + current month → "curso"</li>
     *   <li>Row exists + OPEN + past month → "abierto"</li>
     *   <li>No row + current month → create OPEN, materialize recurrentes → "curso"</li>
     *   <li>No row + past month → "cerrado" (legacy data, no row created)</li>
     * </ul>
     */
    @Transactional
    public String getStatus(User user, int year, int month, LocalDate today) {
        LocalDate currentMonthStart = today.withDayOfMonth(1);
        LocalDate firstDay = LocalDate.of(year, month, 1);

        if (firstDay.isAfter(currentMonthStart)) {
            return "proyectado";
        }

        Optional<MonthPeriod> existing = monthPeriodRepository.findByUser_IdAndYearAndMonth(
                user.getId(), year, month);

        if (existing.isPresent()) {
            MonthPeriod mp = existing.get();
            if ("CLOSED".equals(mp.getStatus())) {
                return "cerrado";
            }
            if ("PROJECTED".equals(mp.getStatus())) {
                return "proyectado";
            }
            // OPEN
            if (firstDay.equals(currentMonthStart)) {
                return "curso";
            }
            return "abierto";
        }

        // No row exists
        if (firstDay.equals(currentMonthStart)) {
            // First access to current month — create record and materialize
            MonthPeriod mp = createOpenPeriod(user, year, month);
            materializeRecurring(user, year, month);
            mp.setRecurringMaterializedAt(OffsetDateTime.now(ZoneOffset.UTC));
            monthPeriodRepository.save(mp);
            return "curso";
        }

        // Past month with no record → legacy data, treat as closed
        return "cerrado";
    }

    /**
     * Closes a period. Idempotent: if already CLOSED, does nothing.
     */
    @Transactional
    public void closePeriod(User user, int year, int month) {
        MonthPeriod mp = monthPeriodRepository
                .findByUser_IdAndYearAndMonth(user.getId(), year, month)
                .orElseGet(() -> createOpenPeriod(user, year, month));

        if ("CLOSED".equals(mp.getStatus())) {
            return; // already closed — idempotent
        }

        mp.setStatus("CLOSED");
        mp.setClosedAt(OffsetDateTime.now(ZoneOffset.UTC));
        monthPeriodRepository.save(mp);
        log.info("Period {}/{} closed for user {}", year, month, user.getId());
    }

    /**
     * Opens or re-opens a period.
     *
     * @return {@code true} if recurrentes were materialized (first open),
     *         {@code false} if they were already materialized (re-open).
     */
    @Transactional
    public boolean openPeriod(User user, int year, int month) {
        MonthPeriod mp = monthPeriodRepository
                .findByUser_IdAndYearAndMonth(user.getId(), year, month)
                .orElseGet(() -> createOpenPeriod(user, year, month));

        mp.setStatus("OPEN");
        mp.setOpenedAt(OffsetDateTime.now(ZoneOffset.UTC));
        mp.setClosedAt(null);

        // Delete any previously projected transactions before materializing real ones
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to   = from.withDayOfMonth(from.lengthOfMonth());
        transactionRepository.deleteByUserAndTransactionDateBetweenAndIsProjectedTrue(user, from, to);

        boolean materialized = false;
        if (mp.getRecurringMaterializedAt() == null) {
            materializeRecurring(user, year, month);
            mp.setRecurringMaterializedAt(OffsetDateTime.now(ZoneOffset.UTC));
            materialized = true;
        }

        monthPeriodRepository.save(mp);
        log.info("Period {}/{} opened for user {} — materialized={}", year, month, user.getId(), materialized);
        return materialized;
    }

    /**
     * Confirms a projection for a future month.
     * Creates/updates a MonthPeriod with status='PROJECTED' and materializes
     * projected transactions from recurring movements and budgets.
     *
     * @throws IllegalArgumentException if the month is not in the future
     */
    @Transactional
    public MonthPeriod confirmProjection(User user, int year, int month) {
        LocalDate today = LocalDate.now();
        boolean isFuture = year > today.getYear()
                || (year == today.getYear() && month > today.getMonthValue());
        if (!isFuture) {
            throw new IllegalArgumentException("Solo se pueden proyectar meses futuros");
        }

        // Create or update MonthPeriod with PROJECTED status
        MonthPeriod mp = monthPeriodRepository
                .findByUser_IdAndYearAndMonth(user.getId(), year, month)
                .orElseGet(() -> MonthPeriod.builder()
                        .user(user)
                        .year(year)
                        .month(month)
                        .build());

        mp.setStatus("PROJECTED");
        mp.setOpenedAt(OffsetDateTime.now(ZoneOffset.UTC));
        mp.setClosedAt(null);
        MonthPeriod saved = monthPeriodRepository.save(mp);

        // Delete any previously projected transactions for this month
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to   = from.withDayOfMonth(from.lengthOfMonth());
        transactionRepository.deleteByUserAndTransactionDateBetweenAndIsProjectedTrue(user, from, to);

        // Materialize projected transactions
        materializeProjected(user, year, month);

        log.info("Projection confirmed for user {} — {}/{}", user.getId(), year, month);
        return saved;
    }

    /**
     * Returns {@code true} when transactions can be posted to this month.
     * Only a month with an explicit CLOSED record blocks posting.
     * Future months (proyectado) and the current month without a record are always writable.
     * Past months without a record are treated as closed (legacy compatibility).
     */
    @Transactional(readOnly = true)
    public boolean isOpen(UUID userId, int year, int month) {
        LocalDate today = LocalDate.now();
        LocalDate currentMonthStart = today.withDayOfMonth(1);
        LocalDate firstDay = LocalDate.of(year, month, 1);

        // Future months are always writable (proyectado)
        if (firstDay.isAfter(currentMonthStart)) {
            return true;
        }

        Optional<MonthPeriod> mp = monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month);

        if (mp.isPresent()) {
            return "OPEN".equals(mp.get().getStatus());
        }

        // No record: current month is implicitly open, past months are implicitly closed
        return firstDay.equals(currentMonthStart);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scheduled tasks
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Every month on the 10th at midnight: auto-close all OPEN periods
     * belonging to months before the current month.
     */
    @Scheduled(cron = "0 0 0 10 * *")
    @Transactional
    public void autoCloseExpiredPeriods() {
        LocalDate today = LocalDate.now();
        List<MonthPeriod> expired = monthPeriodRepository
                .findAllExpiredOpen(today.getYear(), today.getMonthValue());

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (MonthPeriod mp : expired) {
            mp.setStatus("CLOSED");
            mp.setClosedAt(now);
        }
        monthPeriodRepository.saveAll(expired);
        log.info("autoCloseExpiredPeriods: closed {} period(s)", expired.size());
    }

    /**
     * 1st of every month at 00:01: open the new month for every user
     * and materialize their recurring movements.
     */
    @Scheduled(cron = "0 1 0 1 * *")
    public void openNewMonth() {
        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int month = today.getMonthValue();

        List<User> users = userRepository.findAll();
        for (User user : users) {
            try {
                openPeriod(user, year, month);
            } catch (Exception ex) {
                log.error("openNewMonth: failed for user {} — {}", user.getId(), ex.getMessage(), ex);
            }
        }
        log.info("openNewMonth: processed {} user(s) for {}/{}", users.size(), year, month);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private MonthPeriod createOpenPeriod(User user, int year, int month) {
        MonthPeriod mp = MonthPeriod.builder()
                .user(user)
                .year(year)
                .month(month)
                .status("OPEN")
                .openedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
        return monthPeriodRepository.save(mp);
    }

    /**
     * Creates one Transaction for every active recurring movement of the user,
     * assigning transactionDate and dueDate to the correct day within the target month.
     * Uses saveAll() for a single batch insert.
     */
    private void materializeRecurring(User user, int year, int month) {
        List<RecurringMovement> recurrentes =
                recurringMovementRepository.findAllByUser_IdAndActiveTrueAndDeletedAtIsNull(user.getId());

        if (recurrentes.isEmpty()) {
            return;
        }

        LocalDate firstDay = LocalDate.of(year, month, 1);
        int lastDay = firstDay.lengthOfMonth();

        List<Transaction> toSave = new ArrayList<>(recurrentes.size());
        for (RecurringMovement rm : recurrentes) {
            int day = Math.min(rm.getDayOfMonth(), lastDay);
            LocalDate date = LocalDate.of(year, month, day);

            Transaction tx = Transaction.builder()
                    .user(user)
                    .type(rm.getType())
                    .description(rm.getDescription())
                    .amount(rm.getAmount())
                    .ccy(rm.getCcy())
                    .category(rm.getCategory())
                    .account(rm.getAccount())
                    .card(rm.getCard())
                    .transactionDate(date)
                    .dueDate(date)
                    .installment(false)
                    .build();

            toSave.add(tx);
        }

        transactionRepository.saveAll(toSave);
        log.info("materializeRecurring: created {} transactions for user {} in {}/{}",
                toSave.size(), user.getId(), year, month);
    }

    /**
     * Materializes projected transactions for a future month from:
     * 1. Active recurring movements (isProjected=true)
     * 2. Category budgets for categories not already covered by a recurring movement (isProjected=true)
     */
    private void materializeProjected(User user, int year, int month) {
        List<RecurringMovement> recurrentes =
                recurringMovementRepository.findAllByUser_IdAndActiveTrueAndDeletedAtIsNull(user.getId());

        YearMonth ym = YearMonth.of(year, month);
        int lastDay = ym.lengthOfMonth();
        LocalDate firstDay = ym.atDay(1);

        List<Transaction> toSave = new ArrayList<>();

        // Track which category IDs are covered by a recurring movement
        Set<UUID> coveredCategoryIds = recurrentes.stream()
                .filter(rm -> rm.getCategory() != null)
                .map(rm -> rm.getCategory().getId())
                .collect(Collectors.toSet());

        // 1. Recurring movements → projected transactions
        for (RecurringMovement rm : recurrentes) {
            int day = Math.min(rm.getDayOfMonth(), lastDay);
            LocalDate date = LocalDate.of(year, month, day);

            Transaction tx = Transaction.builder()
                    .user(user)
                    .type(rm.getType())
                    .description(rm.getDescription())
                    .amount(rm.getAmount().abs())
                    .ccy(rm.getCcy())
                    .category(rm.getCategory())
                    .account(rm.getAccount())
                    .card(rm.getCard())
                    .transactionDate(date)
                    .dueDate(date)
                    .installment(false)
                    .isProjected(true)
                    .build();

            toSave.add(tx);
        }

        // 2. Budget-only categories (no recurring movement covers them)
        List<CategoryBudget> budgets =
                categoryBudgetRepository.findLatestPerCategoryOnOrBefore(user.getId(), firstDay);

        for (CategoryBudget cb : budgets) {
            if (cb.getCategory() == null) continue;
            UUID catId = cb.getCategory().getId();
            if (coveredCategoryIds.contains(catId)) continue;

            String type = cb.getCategory().getType() == CategoryType.INCOME ? "INCOME" : "EXPENSE";

            Transaction tx = Transaction.builder()
                    .user(user)
                    .type(type)
                    .description("Presupuesto: " + cb.getCategory().getName())
                    .amount(cb.getAmount())
                    .ccy("ARS") // budgets are always in ARS
                    .category(cb.getCategory())
                    .account(cb.getAccount())
                    .transactionDate(firstDay)
                    .dueDate(firstDay)
                    .installment(false)
                    .isProjected(true)
                    .build();

            toSave.add(tx);
        }

        if (!toSave.isEmpty()) {
            transactionRepository.saveAll(toSave);
        }
        log.info("materializeProjected: created {} projected transactions for user {} in {}/{}",
                toSave.size(), user.getId(), year, month);
    }
}
