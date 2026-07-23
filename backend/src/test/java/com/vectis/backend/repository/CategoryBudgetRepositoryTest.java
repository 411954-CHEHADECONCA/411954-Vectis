package com.vectis.backend.repository;

import com.vectis.backend.domain.entity.Category;
import com.vectis.backend.domain.entity.CategoryBudget;
import com.vectis.backend.domain.entity.CategoryType;
import com.vectis.backend.domain.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class CategoryBudgetRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private CategoryBudgetRepository categoryBudgetRepository;

    private User user;
    private Category category;

    @BeforeEach
    void setUp() {
        user = em.persistFlushFind(User.builder()
                .email("user@vectis.com").passwordHash("hash").fullName("Test User").build());
        category = em.persistFlushFind(Category.builder()
                .name("Alimentos").icon("shopping-cart").color("#F59E0B")
                .type(CategoryType.EXPENSE).isDefault(true).build());
    }

    private CategoryBudget.CategoryBudgetBuilder baseBudget(LocalDate validFrom, BigDecimal amount) {
        return CategoryBudget.builder().user(user).category(category).validFrom(validFrom).amount(amount);
    }

    @Test
    @DisplayName("findAllByUser_IdAndValidFromEager: trae los presupuestos con la categoría ya cargada (JOIN FETCH), "
            + "sólo para la fecha exacta pedida")
    void findAllByUser_IdAndValidFromEager_returnsExactMonthWithCategoryFetched() {
        em.persistAndFlush(baseBudget(LocalDate.of(2026, 7, 1), new BigDecimal("50000.0000")).build());
        em.persistAndFlush(baseBudget(LocalDate.of(2026, 6, 1), new BigDecimal("40000.0000")).build());

        List<CategoryBudget> result = categoryBudgetRepository.findAllByUser_IdAndValidFromEager(
                user.getId(), LocalDate.of(2026, 7, 1));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAmount()).isEqualByComparingTo("50000.0000");
        assertThat(result.get(0).getCategory().getName()).isEqualTo("Alimentos");
    }

    @Test
    @DisplayName("findLatestPerCategoryOnOrBefore: por cada categoría, el presupuesto vigente más reciente "
            + "cuyo validFrom sea <= la fecha pedida (no trae uno futuro)")
    void findLatestPerCategoryOnOrBefore_returnsMostRecentUpToDate() {
        em.persistAndFlush(baseBudget(LocalDate.of(2026, 5, 1), new BigDecimal("30000.0000")).build());
        em.persistAndFlush(baseBudget(LocalDate.of(2026, 7, 1), new BigDecimal("50000.0000")).build());
        em.persistAndFlush(baseBudget(LocalDate.of(2026, 9, 1), new BigDecimal("99999.0000")).build()); // futuro, no debe salir

        List<CategoryBudget> result = categoryBudgetRepository.findLatestPerCategoryOnOrBefore(
                user.getId(), LocalDate.of(2026, 7, 15));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAmount()).isEqualByComparingTo("50000.0000");
    }

    @Test
    @DisplayName("findLatestPerCategoryOnOrBefore: mantiene el presupuesto histórico de un mes cerrado "
            + "aunque hoy exista uno más nuevo (no se ve afectado por ediciones posteriores)")
    void findLatestPerCategoryOnOrBefore_respectsHistoricalValueForClosedMonth() {
        em.persistAndFlush(baseBudget(LocalDate.of(2026, 6, 1), new BigDecimal("40000.0000")).build());
        em.persistAndFlush(baseBudget(LocalDate.of(2026, 7, 1), new BigDecimal("50000.0000")).build());

        List<CategoryBudget> result = categoryBudgetRepository.findLatestPerCategoryOnOrBefore(
                user.getId(), LocalDate.of(2026, 6, 30));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAmount()).isEqualByComparingTo("40000.0000");
    }
}
