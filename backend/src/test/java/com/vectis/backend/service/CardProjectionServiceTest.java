package com.vectis.backend.service;

import com.vectis.backend.domain.entity.CreditCard;
import com.vectis.backend.domain.entity.Transaction;
import com.vectis.backend.domain.entity.TransactionType;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.CardMatrixResponse;
import com.vectis.backend.dto.CardOverviewResponse;
import com.vectis.backend.repository.CreditCardRepository;
import com.vectis.backend.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("CardProjectionService")
class CardProjectionServiceTest {

    @Mock private CreditCardRepository creditCardRepository;
    @Mock private TransactionRepository transactionRepository;

    private CardProjectionService service;

    private User user;
    private UUID userId;
    private CreditCard card;

    @BeforeEach
    void setUp() {
        service = new CardProjectionService(creditCardRepository, transactionRepository, new CardCycle());
        userId = UUID.randomUUID();
        user = User.builder().id(userId).email("u@v.com").fullName("U").passwordHash("h").build();
        card = CreditCard.builder()
                .id(UUID.randomUUID()).user(user).bank("Galicia").network("Visa").last4("4821")
                .ccy("ARS").creditLimit(new BigDecimal("1500000")).closingDay(5).dueDay(15).accent("#52eacd")
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();
    }

    @Test
    @DisplayName("overview suma el consumido y arma las cuotas activas con la cuota actual correcta")
    void overview_consumidoAndCuotasActivas() {
        UUID group = UUID.randomUUID();
        LocalDate m0 = YearMonth.now().atDay(15);
        List<Transaction> debt = List.of(
                installment(group, 2, 6, "20000", m0, "Notebook"),
                installment(group, 3, 6, "20000", YearMonth.now().plusMonths(1).atDay(15), "Notebook"),
                installment(group, 4, 6, "20000", YearMonth.now().plusMonths(2).atDay(15), "Notebook"),
                single("10000", m0, "Carga combustible"));

        given(creditCardRepository.findAllByUser_IdOrderByCreatedAtAsc(userId)).willReturn(List.of(card));
        given(transactionRepository.findCardDebt(eq(userId), any())).willReturn(debt);

        CardOverviewResponse ov = service.overview(userId);

        assertThat(ov.cards()).hasSize(1);
        assertThat(ov.cards().get(0).consumido()).isEqualByComparingTo("70000"); // 20000*3 + 10000
        assertThat(ov.totalDue()).isEqualByComparingTo("70000");

        assertThat(ov.cuotasActivas()).hasSize(1);
        var plan = ov.cuotasActivas().get(0);
        assertThat(plan.description()).isEqualTo("Notebook");
        assertThat(plan.currentNumber()).isEqualTo(2);
        assertThat(plan.totalInstallments()).isEqualTo(6);
        assertThat(plan.monthlyAmount()).isEqualByComparingTo("20000");
        assertThat(plan.cardName()).isEqualTo("Galicia ····4821");

        // Próximo vencimiento real = primer mes con deuda (este mes): cuota 20000 + single 10000.
        assertThat(ov.vencimientos()).hasSize(1);
        assertThat(ov.vencimientos().get(0).dueDate()).isEqualTo(m0);
        assertThat(ov.vencimientos().get(0).amount()).isEqualByComparingTo("30000");
        assertThat(ov.nextDueDate()).isEqualTo(m0);
    }

    @Test
    @DisplayName("matrix devuelve N columnas y ubica cada cuota en su mes, con subtotales y totales exactos")
    void matrix_placesInstallmentsWithTotals() {
        UUID group = UUID.randomUUID();
        List<Transaction> debt = List.of(
                installment(group, 1, 3, "20000", YearMonth.now().atDay(15), "Notebook"),
                installment(group, 2, 3, "20000", YearMonth.now().plusMonths(1).atDay(15), "Notebook"),
                installment(group, 3, 3, "20000", YearMonth.now().plusMonths(2).atDay(15), "Notebook"),
                single("5000", YearMonth.now().atDay(20), "Snack"),
                single("99999", YearMonth.now().plusMonths(8).atDay(10), "Fuera de ventana"));

        given(transactionRepository.findCardTransactionsFromDate(eq(userId), any())).willReturn(debt);

        CardMatrixResponse mx = service.matrix(userId, 6);

        assertThat(mx.months()).hasSize(6);
        assertThat(mx.months().get(0)).isEqualTo(YearMonth.now().toString());
        assertThat(mx.cards()).hasSize(1);

        var cardRow = mx.cards().get(0);
        assertThat(cardRow.cardName()).isEqualTo("Galicia ····4821");
        // El consumo fuera de la ventana (mes +8) no aparece como fila.
        assertThat(cardRow.consumos()).hasSize(2);

        var notebook = cardRow.consumos().stream().filter(c -> c.description().equals("Notebook")).findFirst().orElseThrow();
        assertThat(notebook.installmentLabel()).isEqualTo("1/3");
        assertThat(notebook.cellsByMonth().get(0)).isEqualByComparingTo("20000");
        assertThat(notebook.cellsByMonth().get(1)).isEqualByComparingTo("20000");
        assertThat(notebook.cellsByMonth().get(2)).isEqualByComparingTo("20000");
        assertThat(notebook.cellsByMonth().get(3)).isNull();

        // Subtotal mes 0 = 20000 (notebook) + 5000 (snack) = 25000
        assertThat(cardRow.subtotalsByMonth().get(0)).isEqualByComparingTo("25000");
        assertThat(mx.totalsByMonth().get(0)).isEqualByComparingTo("25000");
        assertThat(mx.totalsByMonth().get(1)).isEqualByComparingTo("20000");
        assertThat(mx.totalsByMonth().get(2)).isEqualByComparingTo("20000");
        assertThat(mx.totalsByMonth().get(5)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("matrix incluye extraCharges en la columna del mes correcto y los suma al subtotal")
    void matrix_includesExtraChargesInCorrectMonth() {
        UUID group     = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        LocalDate today = YearMonth.now().atDay(15);

        List<Transaction> debt = List.of(
                installment(group, 1, 2, "20000", today, "Notebook"));

        Transaction extraCharge = Transaction.builder()
                .id(UUID.randomUUID()).user(user).type(TransactionType.EXPENSE)
                .description("Intereses financieros").amount(new BigDecimal("2000")).ccy("ARS")
                .transactionDate(today).dueDate(today).installment(false)
                .cardPaymentId(paymentId).createdAt(OffsetDateTime.now()).build();

        given(transactionRepository.findCardTransactionsFromDate(eq(userId), any())).willReturn(debt);
        given(transactionRepository.findExtraChargesForCard(eq(card.getId()), any())).willReturn(List.of(extraCharge));

        CardMatrixResponse mx = service.matrix(userId, 6);

        assertThat(mx.cards()).hasSize(1);
        var cardRow = mx.cards().get(0);
        assertThat(cardRow.extraCharges()).hasSize(1);

        var extra = cardRow.extraCharges().get(0);
        assertThat(extra.description()).isEqualTo("Intereses financieros");
        assertThat(extra.installmentLabel()).isNull();
        assertThat(extra.cellsByMonth().get(0)).isEqualByComparingTo("2000");
        assertThat(extra.paidByMonth().get(0)).isTrue();

        // El cargo extra suma al subtotal y al total consolidado
        assertThat(cardRow.subtotalsByMonth().get(0)).isEqualByComparingTo("22000");
        assertThat(mx.totalsByMonth().get(0)).isEqualByComparingTo("22000");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Transaction installment(UUID group, int number, int total, String amount, LocalDate due, String base) {
        return Transaction.builder()
                .id(UUID.randomUUID()).user(user).type(TransactionType.EXPENSE)
                .description(base + " — cuota " + number + "/" + total)
                .amount(new BigDecimal(amount)).ccy("ARS").card(card)
                .transactionDate(due).dueDate(due)
                .installment(true).installmentNumber(number).totalInstallments(total).installmentGroupId(group)
                .createdAt(OffsetDateTime.now()).build();
    }

    private Transaction single(String amount, LocalDate due, String desc) {
        return Transaction.builder()
                .id(UUID.randomUUID()).user(user).type(TransactionType.EXPENSE)
                .description(desc).amount(new BigDecimal(amount)).ccy("ARS").card(card)
                .transactionDate(due).dueDate(due).installment(false)
                .createdAt(OffsetDateTime.now()).build();
    }
}
