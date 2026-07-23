package com.vectis.backend.repository;

import com.vectis.backend.domain.entity.Category;
import com.vectis.backend.domain.entity.CategoryType;
import com.vectis.backend.domain.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class CategoryRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private CategoryRepository categoryRepository;

    private User user;
    private User otherUser;

    @BeforeEach
    void setUp() {
        user = em.persistFlushFind(User.builder()
                .email("user@vectis.com").passwordHash("hash").fullName("Test User").build());
        otherUser = em.persistFlushFind(User.builder()
                .email("other@vectis.com").passwordHash("hash").fullName("Other User").build());
    }

    @Test
    @DisplayName("findAllForUser: trae las categorías propias del usuario más las de sistema (user=null), "
            + "sin traer las de otro usuario")
    void findAllForUser_returnsOwnAndSystemCategoriesOnly() {
        em.persistAndFlush(Category.builder()
                .user(null).name("Otros egresos").icon("circle").color("#9CA3AF")
                .type(CategoryType.EXPENSE).isDefault(true).build());
        em.persistAndFlush(Category.builder()
                .user(user).name("Mi categoría").icon("star").color("#000000")
                .type(CategoryType.EXPENSE).isDefault(false).build());
        em.persistAndFlush(Category.builder()
                .user(otherUser).name("Categoría ajena").icon("star").color("#000000")
                .type(CategoryType.EXPENSE).isDefault(false).build());

        List<Category> result = categoryRepository.findAllForUser(user.getId());

        assertThat(result).extracting(Category::getName)
                .containsExactlyInAnyOrder("Otros egresos", "Mi categoría");
    }

    @Test
    @DisplayName("findAllForUser: ordena por isDefault descendente y luego por nombre ascendente")
    void findAllForUser_ordersByIsDefaultThenName() {
        em.persistAndFlush(Category.builder()
                .user(user).name("Zebra propia").icon("star").color("#000000")
                .type(CategoryType.EXPENSE).isDefault(false).build());
        em.persistAndFlush(Category.builder()
                .user(null).name("Sistema B").icon("circle").color("#9CA3AF")
                .type(CategoryType.EXPENSE).isDefault(true).build());
        em.persistAndFlush(Category.builder()
                .user(null).name("Sistema A").icon("circle").color("#9CA3AF")
                .type(CategoryType.EXPENSE).isDefault(true).build());

        List<Category> result = categoryRepository.findAllForUser(user.getId());

        assertThat(result).extracting(Category::getName)
                .containsExactly("Sistema A", "Sistema B", "Zebra propia");
    }

    @Test
    @DisplayName("findByTypeAndIsUncategorizedDefaultTrue: trae la categoría default por tipo, ignorando otras is_default")
    void findByTypeAndIsUncategorizedDefaultTrue_returnsMarkedCategory() {
        em.persistAndFlush(Category.builder()
                .user(null).name("Otros egresos").icon("circle").color("#9CA3AF")
                .type(CategoryType.EXPENSE).isDefault(true).isUncategorizedDefault(true).build());
        em.persistAndFlush(Category.builder()
                .user(null).name("Alimentos").icon("utensils").color("#10B981")
                .type(CategoryType.EXPENSE).isDefault(true).isUncategorizedDefault(false).build());

        Optional<Category> result = categoryRepository.findByTypeAndIsUncategorizedDefaultTrue(CategoryType.EXPENSE);

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Otros egresos");
    }

    @Test
    @DisplayName("findByTypeAndIsUncategorizedDefaultTrue: vacío si no hay ninguna marcada para ese tipo")
    void findByTypeAndIsUncategorizedDefaultTrue_emptyWhenNoneMarked() {
        em.persistAndFlush(Category.builder()
                .user(null).name("Sueldo").icon("briefcase").color("#10B981")
                .type(CategoryType.INCOME).isDefault(true).isUncategorizedDefault(false).build());

        Optional<Category> result = categoryRepository.findByTypeAndIsUncategorizedDefaultTrue(CategoryType.INCOME);

        assertThat(result).isEmpty();
    }
}
