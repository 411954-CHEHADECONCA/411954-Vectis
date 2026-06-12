package com.vectis.backend.mapper;

import com.vectis.backend.domain.entity.Account;
import com.vectis.backend.domain.entity.Category;
import com.vectis.backend.domain.entity.CategoryType;
import com.vectis.backend.domain.entity.CreditCard;
import com.vectis.backend.domain.entity.Transaction;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.MovementResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TransactionMapper")
class TransactionMapperTest {

    private final TransactionMapper mapper = new TransactionMapper();

    @Test
    @DisplayName("toResponse mapea categoría, tarjeta y datos de cuota")
    void toResponse_withCategoryAndCard_mapsAllFields() {
        User user = User.builder().id(UUID.randomUUID()).email("u@v.com").fullName("U").passwordHash("h").build();
        Category cat = Category.builder().id(UUID.randomUUID()).name("Tarjetas").icon("card").color("#abc").type(CategoryType.EXPENSE).build();
        CreditCard card = CreditCard.builder()
                .id(UUID.randomUUID()).user(user).bank("Galicia").network("Visa").last4("4821")
                .ccy("ARS").creditLimit(BigDecimal.TEN).closingDay(5).dueDay(15).accent("#52eacd")
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();

        Transaction tx = Transaction.builder()
                .id(UUID.randomUUID()).user(user).type("EXPENSE").description("Notebook — cuota 3/6")
                .amount(new BigDecimal("612300.0000")).ccy("ARS")
                .category(cat).card(card)
                .transactionDate(LocalDate.of(2026, 4, 7)).dueDate(LocalDate.of(2026, 7, 15))
                .installment(true).installmentNumber(3).totalInstallments(6)
                .installmentGroupId(UUID.randomUUID()).createdAt(OffsetDateTime.now())
                .build();

        MovementResponse r = mapper.toResponse(tx);

        assertThat(r.description()).isEqualTo("Notebook — cuota 3/6");
        assertThat(r.categoryName()).isEqualTo("Tarjetas");
        assertThat(r.categoryIcon()).isEqualTo("card");
        assertThat(r.categoryColor()).isEqualTo("#abc");
        assertThat(r.cardName()).isEqualTo("Galicia ····4821");
        assertThat(r.accountId()).isNull();
        assertThat(r.installment()).isTrue();
        assertThat(r.installmentNumber()).isEqualTo(3);
        assertThat(r.totalInstallments()).isEqualTo(6);
        assertThat(r.dueDate()).isEqualTo(LocalDate.of(2026, 7, 15));
    }

    @Test
    @DisplayName("toResponse con cuenta y sin categoría deja nulos los campos de categoría/tarjeta")
    void toResponse_withAccountNoCategory_nullSafe() {
        User user = User.builder().id(UUID.randomUUID()).email("u@v.com").fullName("U").passwordHash("h").build();
        Account account = Account.builder()
                .id(UUID.randomUUID()).user(user).name("Galicia").kind("Banco").ccy("ARS")
                .balance(BigDecimal.ZERO).remunerada(false)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();

        Transaction tx = Transaction.builder()
                .id(UUID.randomUUID()).user(user).type("INCOME").description("Sueldo")
                .amount(new BigDecimal("1240000.0000")).ccy("ARS").account(account)
                .transactionDate(LocalDate.of(2026, 6, 10)).dueDate(LocalDate.of(2026, 6, 10))
                .installment(false).createdAt(OffsetDateTime.now())
                .build();

        MovementResponse r = mapper.toResponse(tx);

        assertThat(r.accountName()).isEqualTo("Galicia");
        assertThat(r.categoryId()).isNull();
        assertThat(r.categoryName()).isNull();
        assertThat(r.cardId()).isNull();
        assertThat(r.cardName()).isNull();
        assertThat(r.installment()).isFalse();
    }
}
