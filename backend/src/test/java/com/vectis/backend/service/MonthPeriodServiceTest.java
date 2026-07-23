package com.vectis.backend.service;

import com.vectis.backend.domain.entity.MonthPeriod;
import com.vectis.backend.domain.entity.RecurringMovement;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.repository.CategoryBudgetRepository;
import com.vectis.backend.repository.MonthPeriodRepository;
import com.vectis.backend.repository.RecurringMovementRepository;
import com.vectis.backend.repository.TransactionRepository;
import com.vectis.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MonthPeriodService")
class MonthPeriodServiceTest {

    @InjectMocks
    private MonthPeriodService monthPeriodService;

    @Mock private MonthPeriodRepository        monthPeriodRepository;
    @Mock private RecurringMovementRepository  recurringMovementRepository;
    @Mock private TransactionRepository        transactionRepository;
    @Mock private UserRepository               userRepository;
    @Mock private CategoryBudgetRepository     categoryBudgetRepository;
    @Mock private InvestmentMonthCloseService  investmentMonthCloseService;

    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId).email("user@vectis.com")
                .fullName("Test User").passwordHash("hash")
                .build();
    }

    // ─── getStatus ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getStatus mes futuro retorna proyectado")
    void getStatus_mesFuturo_retornaProyectado() {
        LocalDate today = LocalDate.now();
        LocalDate future = today.plusMonths(1);

        String status = monthPeriodService.getStatus(user, future.getYear(), future.getMonthValue(), today);

        assertThat(status).isEqualTo("proyectado");
        verifyNoInteractions(monthPeriodRepository);
    }

    @Test
    @DisplayName("getStatus registro PROJECTED retorna proyectado")
    void getStatus_registroProjected_retornaProyectado() {
        LocalDate today = LocalDate.now();
        // Use a past month so it goes through the DB lookup path
        LocalDate past  = today.minusMonths(1);
        int year  = past.getYear();
        int month = past.getMonthValue();

        MonthPeriod mp = buildPeriodWithStatus(year, month, "PROJECTED");
        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month))
                .willReturn(Optional.of(mp));

        String status = monthPeriodService.getStatus(user, year, month, today);

        assertThat(status).isEqualTo("proyectado");
    }

    @Test
    @DisplayName("getStatus mes actual sin registro crea registro, materializa recurrentes y retorna curso")
    void getStatus_mesActualSinRegistro_creaRegistroMaterializaYRetornaCurso() {
        LocalDate today = LocalDate.now();
        int year  = today.getYear();
        int month = today.getMonthValue();

        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month))
                .willReturn(Optional.empty());

        MonthPeriod saved = buildOpenPeriod(year, month);
        given(monthPeriodRepository.save(any(MonthPeriod.class))).willReturn(saved);
        given(recurringMovementRepository.findAllByUser_IdAndActiveTrueAndDeletedAtIsNull(userId))
                .willReturn(Collections.emptyList());

        String status = monthPeriodService.getStatus(user, year, month, today);

        assertThat(status).isEqualTo("curso");
        // save called at least twice: once to create, once to set recurringMaterializedAt
        verify(monthPeriodRepository, atLeast(2)).save(any(MonthPeriod.class));
    }

    @Test
    @DisplayName("getStatus mes pasado sin registro retorna cerrado sin crear registro")
    void getStatus_mesPasadoSinRegistro_retornaCerrado_sinCrearRegistro() {
        LocalDate today = LocalDate.now();
        LocalDate past  = today.minusMonths(1);
        int year  = past.getYear();
        int month = past.getMonthValue();

        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month))
                .willReturn(Optional.empty());

        String status = monthPeriodService.getStatus(user, year, month, today);

        assertThat(status).isEqualTo("cerrado");
        verify(monthPeriodRepository, never()).save(any(MonthPeriod.class));
    }

    @Test
    @DisplayName("getStatus registro OPEN mes actual retorna curso")
    void getStatus_registroOpenMesActual_retornaCurso() {
        LocalDate today = LocalDate.now();
        int year  = today.getYear();
        int month = today.getMonthValue();

        MonthPeriod mp = buildOpenPeriod(year, month);
        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month))
                .willReturn(Optional.of(mp));

        String status = monthPeriodService.getStatus(user, year, month, today);

        assertThat(status).isEqualTo("curso");
    }

    @Test
    @DisplayName("getStatus registro OPEN mes pasado retorna abierto")
    void getStatus_registroOpenMesPasado_retornaAbierto() {
        LocalDate today = LocalDate.now();
        LocalDate past  = today.minusMonths(1);
        int year  = past.getYear();
        int month = past.getMonthValue();

        MonthPeriod mp = buildOpenPeriod(year, month);
        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month))
                .willReturn(Optional.of(mp));

        String status = monthPeriodService.getStatus(user, year, month, today);

        assertThat(status).isEqualTo("abierto");
    }

    @Test
    @DisplayName("getStatus registro CLOSED retorna cerrado")
    void getStatus_registroClosed_retornaCerrado() {
        LocalDate today = LocalDate.now();
        LocalDate past  = today.minusMonths(1);
        int year  = past.getYear();
        int month = past.getMonthValue();

        MonthPeriod mp = buildClosedPeriod(year, month);
        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month))
                .willReturn(Optional.of(mp));

        String status = monthPeriodService.getStatus(user, year, month, today);

        assertThat(status).isEqualTo("cerrado");
    }

    // ─── openPeriod ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("openPeriod primera apertura borra proyectadas, materializa recurrentes y retorna true")
    void openPeriod_primeraApertura_materializaRecurrentesRetornaTrue() {
        LocalDate today = LocalDate.now();
        int year  = today.getYear();
        int month = today.getMonthValue();

        MonthPeriod mp = buildOpenPeriod(year, month);
        mp.setRecurringMaterializedAt(null); // not yet materialized

        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month))
                .willReturn(Optional.of(mp));
        given(monthPeriodRepository.save(any(MonthPeriod.class))).willReturn(mp);

        RecurringMovement rm = buildRecurringMovement(user);
        given(recurringMovementRepository.findAllByUser_IdAndActiveTrueAndDeletedAtIsNull(userId))
                .willReturn(List.of(rm));
        given(transactionRepository.saveAll(anyList())).willReturn(Collections.emptyList());

        boolean result = monthPeriodService.openPeriod(user, year, month);

        assertThat(result).isTrue();
        // projected transactions must be deleted before materializing
        verify(transactionRepository).deleteByUserAndTransactionDateBetweenAndIsProjectedTrue(
                eq(user), any(LocalDate.class), any(LocalDate.class));
        verify(transactionRepository).saveAll(anyList());
        assertThat(mp.getRecurringMaterializedAt()).isNotNull();
    }

    @Test
    @DisplayName("openPeriod reapertura borra proyectadas pero no materializa recurrentes, retorna false")
    void openPeriod_reapertura_noMaterializaRetornaFalse() {
        LocalDate today = LocalDate.now();
        int year  = today.getYear();
        int month = today.getMonthValue();

        MonthPeriod mp = buildClosedPeriod(year, month);
        mp.setRecurringMaterializedAt(OffsetDateTime.now()); // already materialized

        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month))
                .willReturn(Optional.of(mp));
        given(monthPeriodRepository.save(any(MonthPeriod.class))).willReturn(mp);

        boolean result = monthPeriodService.openPeriod(user, year, month);

        assertThat(result).isFalse();
        // delete projected is still called even on reopen
        verify(transactionRepository).deleteByUserAndTransactionDateBetweenAndIsProjectedTrue(
                eq(user), any(LocalDate.class), any(LocalDate.class));
        // but saveAll for recurring is NOT called
        verify(transactionRepository, never()).saveAll(anyList());
    }

    // ─── confirmProjection ───────────────────────────────────────────────────

    @Test
    @DisplayName("confirmProjection mes no futuro lanza IllegalArgumentException")
    void confirmProjection_mesNoFuturo_lanzaExcepcion() {
        LocalDate today = LocalDate.now();
        int year  = today.getYear();
        int month = today.getMonthValue();

        assertThatThrownBy(() -> monthPeriodService.confirmProjection(user, year, month))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Solo se pueden proyectar meses futuros");

        verifyNoInteractions(monthPeriodRepository);
    }

    @Test
    @DisplayName("confirmProjection mes pasado lanza IllegalArgumentException")
    void confirmProjection_mesPasado_lanzaExcepcion() {
        LocalDate past = LocalDate.now().minusMonths(1);

        assertThatThrownBy(() ->
                monthPeriodService.confirmProjection(user, past.getYear(), past.getMonthValue()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("confirmProjection mes futuro crea registro PROJECTED y llama saveAll para proyectadas")
    void confirmProjection_mesFuturo_creaRegistroYMaterializa() {
        LocalDate future = LocalDate.now().plusMonths(1);
        int year  = future.getYear();
        int month = future.getMonthValue();

        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month))
                .willReturn(Optional.empty());

        MonthPeriod savedMp = MonthPeriod.builder()
                .id(UUID.randomUUID()).user(user)
                .year(year).month(month).status("PROJECTED")
                .openedAt(OffsetDateTime.now())
                .build();
        given(monthPeriodRepository.save(any(MonthPeriod.class))).willReturn(savedMp);

        given(recurringMovementRepository.findAllByUser_IdAndActiveTrueAndDeletedAtIsNull(userId))
                .willReturn(Collections.emptyList());
        given(categoryBudgetRepository.findLatestPerCategoryOnOrBefore(eq(userId), any(LocalDate.class)))
                .willReturn(Collections.emptyList());

        MonthPeriod result = monthPeriodService.confirmProjection(user, year, month);

        assertThat(result.getStatus()).isEqualTo("PROJECTED");
        // projected transactions deleted before materializing
        verify(transactionRepository).deleteByUserAndTransactionDateBetweenAndIsProjectedTrue(
                eq(user), any(LocalDate.class), any(LocalDate.class));
        // saveAll called (even if empty list — only when toSave is non-empty; verify save was at least attempted)
        verify(monthPeriodRepository).save(any(MonthPeriod.class));
    }

    @Test
    @DisplayName("confirmProjection mes futuro con recurrentes llama transactionRepository.saveAll")
    void confirmProjection_mesFuturo_conRecurrentes_llamaSaveAll() {
        LocalDate future = LocalDate.now().plusMonths(1);
        int year  = future.getYear();
        int month = future.getMonthValue();

        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month))
                .willReturn(Optional.empty());

        MonthPeriod savedMp = MonthPeriod.builder()
                .id(UUID.randomUUID()).user(user)
                .year(year).month(month).status("PROJECTED")
                .openedAt(OffsetDateTime.now())
                .build();
        given(monthPeriodRepository.save(any(MonthPeriod.class))).willReturn(savedMp);

        RecurringMovement rm = buildRecurringMovement(user);
        given(recurringMovementRepository.findAllByUser_IdAndActiveTrueAndDeletedAtIsNull(userId))
                .willReturn(List.of(rm));
        given(categoryBudgetRepository.findLatestPerCategoryOnOrBefore(eq(userId), any(LocalDate.class)))
                .willReturn(Collections.emptyList());
        given(transactionRepository.saveAll(anyList())).willReturn(Collections.emptyList());

        monthPeriodService.confirmProjection(user, year, month);

        verify(transactionRepository).saveAll(anyList());
    }

    // ─── autoCloseExpiredPeriods ─────────────────────────────────────────────

    @Test
    @DisplayName("autoCloseExpiredPeriods cierra períodos expirados y materializa sus tramos de inversión")
    void autoCloseExpiredPeriods_cierraExpiredYMaterializaInversiones() {
        LocalDate today = LocalDate.now();
        LocalDate past  = today.minusMonths(1);

        MonthPeriod expiredMp = buildOpenPeriod(past.getYear(), past.getMonthValue());
        expiredMp.setUser(user);

        given(monthPeriodRepository.findAllExpiredOpen(today.getYear(), today.getMonthValue()))
                .willReturn(List.of(expiredMp));
        given(monthPeriodRepository.save(any(MonthPeriod.class))).willReturn(expiredMp);

        monthPeriodService.autoCloseExpiredPeriods();

        ArgumentCaptor<MonthPeriod> captor = ArgumentCaptor.forClass(MonthPeriod.class);
        verify(monthPeriodRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("CLOSED");
        assertThat(captor.getValue().getClosedAt()).isNotNull();
        // los tramos de inversión se materializan para el período cerrado
        verify(investmentMonthCloseService).materializeForMonth(
                eq(user), eq(past.getYear()), eq(past.getMonthValue()));
        assertThat(expiredMp.getInvestmentTramosMaterializedAt()).isNotNull();
    }

    // ─── closePeriod ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("closePeriod cierra y materializa los tramos de inversión una sola vez")
    void closePeriod_cierraYMaterializaUnaVez() {
        LocalDate today = LocalDate.now();
        int year  = today.getYear();
        int month = today.getMonthValue();

        MonthPeriod mp = buildOpenPeriod(year, month);
        mp.setInvestmentTramosMaterializedAt(null);

        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month))
                .willReturn(Optional.of(mp));
        given(monthPeriodRepository.save(any(MonthPeriod.class))).willReturn(mp);

        monthPeriodService.closePeriod(user, year, month);

        assertThat(mp.getStatus()).isEqualTo("CLOSED");
        assertThat(mp.getInvestmentTramosMaterializedAt()).isNotNull();
        verify(investmentMonthCloseService).materializeForMonth(eq(user), eq(year), eq(month));
    }

    @Test
    @DisplayName("closePeriod sobre un mes ya cerrado es no-op (no re-materializa)")
    void closePeriod_yaCerrado_noReMaterializa() {
        LocalDate today = LocalDate.now();
        int year  = today.getYear();
        int month = today.getMonthValue();

        MonthPeriod mp = buildClosedPeriod(year, month);
        mp.setInvestmentTramosMaterializedAt(OffsetDateTime.now());

        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month))
                .willReturn(Optional.of(mp));

        monthPeriodService.closePeriod(user, year, month);

        verify(investmentMonthCloseService, never()).materializeForMonth(any(), anyInt(), anyInt());
        verify(monthPeriodRepository, never()).save(any(MonthPeriod.class));
    }

    @Test
    @DisplayName("openPeriod (reapertura) revierte los tramos de inversión y limpia el marcador")
    void openPeriod_reapertura_revierteTramosInversion() {
        LocalDate today = LocalDate.now();
        int year  = today.getYear();
        int month = today.getMonthValue();

        MonthPeriod mp = buildClosedPeriod(year, month);
        mp.setRecurringMaterializedAt(OffsetDateTime.now());
        mp.setInvestmentTramosMaterializedAt(OffsetDateTime.now());

        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month))
                .willReturn(Optional.of(mp));
        given(monthPeriodRepository.save(any(MonthPeriod.class))).willReturn(mp);

        monthPeriodService.openPeriod(user, year, month);

        verify(investmentMonthCloseService).removeForMonth(eq(user), eq(year), eq(month));
        assertThat(mp.getInvestmentTramosMaterializedAt()).isNull();
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private MonthPeriod buildOpenPeriod(int year, int month) {
        return MonthPeriod.builder()
                .id(UUID.randomUUID())
                .user(user)
                .year(year)
                .month(month)
                .status("OPEN")
                .openedAt(OffsetDateTime.now())
                .build();
    }

    private MonthPeriod buildClosedPeriod(int year, int month) {
        MonthPeriod mp = buildOpenPeriod(year, month);
        mp.setStatus("CLOSED");
        mp.setClosedAt(OffsetDateTime.now());
        return mp;
    }

    private MonthPeriod buildPeriodWithStatus(int year, int month, String status) {
        MonthPeriod mp = buildOpenPeriod(year, month);
        mp.setStatus(status);
        return mp;
    }

    private RecurringMovement buildRecurringMovement(User owner) {
        return RecurringMovement.builder()
                .id(UUID.randomUUID())
                .user(owner)
                .description("Netflix")
                .amount(new BigDecimal("15000.0000"))
                .ccy("ARS")
                .type("EXPENSE")
                .dayOfMonth(10)
                .active(true)
                .createdAt(OffsetDateTime.now())
                .build();
    }
}
