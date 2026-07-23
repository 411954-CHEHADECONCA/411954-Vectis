package com.vectis.backend.repository;

import com.vectis.backend.domain.entity.InvestmentAsset;
import com.vectis.backend.domain.entity.InvestmentAssetType;
import com.vectis.backend.domain.entity.InvestmentPayment;
import com.vectis.backend.domain.entity.InvestmentPaymentSource;
import com.vectis.backend.domain.entity.InvestmentPaymentStatus;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class InvestmentPaymentRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private InvestmentPaymentRepository paymentRepository;

    private User user;
    private InvestmentAsset asset;

    @BeforeEach
    void setUp() {
        user = em.persistFlushFind(User.builder()
                .email("user@vectis.com").passwordHash("hash").fullName("Test User").build());

        asset = em.persistFlushFind(InvestmentAsset.builder()
                .user(user).name("AL30").type(InvestmentAssetType.BONO).currency("ARS")
                .principal(new BigDecimal("100000.0000")).purchaseDate(LocalDate.of(2026, 1, 1))
                .tna(BigDecimal.ZERO)
                .build());
    }

    private InvestmentPayment.InvestmentPaymentBuilder basePayment(LocalDate cuttingDate) {
        return InvestmentPayment.builder()
                .investmentAsset(asset)
                .cuttingDate(cuttingDate)
                .rentPer100(new BigDecimal("0.270000"))
                .amortizationPer100(new BigDecimal("8.000000"))
                .currency("USD")
                .source(InvestmentPaymentSource.PPI);
    }

    @Test
    @DisplayName("findAllByInvestmentAsset_IdOrderByCuttingDateAsc: ordena ascendente por fecha de corte")
    void findAllByInvestmentAsset_IdOrderByCuttingDateAsc_ordersAscending() {
        em.persistAndFlush(basePayment(LocalDate.of(2027, 1, 9)).status(InvestmentPaymentStatus.PENDIENTE).build());
        em.persistAndFlush(basePayment(LocalDate.of(2026, 7, 9)).status(InvestmentPaymentStatus.PENDIENTE).build());

        List<InvestmentPayment> result = paymentRepository.findAllByInvestmentAsset_IdOrderByCuttingDateAsc(asset.getId());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCuttingDate()).isEqualTo(LocalDate.of(2026, 7, 9));
        assertThat(result.get(1).getCuttingDate()).isEqualTo(LocalDate.of(2027, 1, 9));
    }

    @Test
    @DisplayName("findByInvestmentAsset_IdAndCuttingDateAndSource: encuentra por clave compuesta")
    void findByInvestmentAsset_IdAndCuttingDateAndSource_findsByCompositeKey() {
        em.persistAndFlush(basePayment(LocalDate.of(2026, 7, 9)).status(InvestmentPaymentStatus.PENDIENTE).build());

        Optional<InvestmentPayment> found = paymentRepository.findByInvestmentAsset_IdAndCuttingDateAndSource(
                asset.getId(), LocalDate.of(2026, 7, 9), InvestmentPaymentSource.PPI);
        Optional<InvestmentPayment> notFound = paymentRepository.findByInvestmentAsset_IdAndCuttingDateAndSource(
                asset.getId(), LocalDate.of(2026, 7, 9), InvestmentPaymentSource.MANUAL);

        assertThat(found).isPresent();
        assertThat(notFound).isEmpty();
    }

    @Test
    @DisplayName("findPendingByUser: sólo PENDIENTE con cutting_date <= la fecha dada, del usuario dueño del activo")
    void findPendingByUser_filtersStatusAndDateAndOwnership() {
        User otherUser = em.persistFlushFind(User.builder()
                .email("other@vectis.com").passwordHash("hash").fullName("Other User").build());
        InvestmentAsset otherAsset = em.persistFlushFind(InvestmentAsset.builder()
                .user(otherUser).name("Otro").type(InvestmentAssetType.ON).currency("USD")
                .principal(BigDecimal.TEN).purchaseDate(LocalDate.of(2026, 1, 1)).tna(BigDecimal.ZERO).build());

        // vencido, pendiente, del usuario: debe aparecer
        em.persistAndFlush(basePayment(LocalDate.of(2026, 7, 1)).status(InvestmentPaymentStatus.PENDIENTE).build());
        // futuro: no debe aparecer
        em.persistAndFlush(basePayment(LocalDate.of(2027, 1, 1)).status(InvestmentPaymentStatus.PENDIENTE).build());
        // vencido pero ya cobrado: no debe aparecer
        em.persistAndFlush(basePayment(LocalDate.of(2026, 6, 1)).status(InvestmentPaymentStatus.COBRADO).build());
        // vencido, pendiente, de otro usuario: no debe aparecer
        em.persistAndFlush(InvestmentPayment.builder()
                .investmentAsset(otherAsset).cuttingDate(LocalDate.of(2026, 7, 1))
                .rentPer100(BigDecimal.ZERO).amortizationPer100(BigDecimal.ZERO)
                .currency("USD").source(InvestmentPaymentSource.PPI)
                .status(InvestmentPaymentStatus.PENDIENTE).build());

        List<InvestmentPayment> result = paymentRepository.findPendingByUser(user.getId(), LocalDate.of(2026, 7, 7));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCuttingDate()).isEqualTo(LocalDate.of(2026, 7, 1));
    }

    @Test
    @DisplayName("sumCollectedAmortizationPer100: suma sólo los pagos COBRADO del activo, 0 si no hay ninguno")
    void sumCollectedAmortizationPer100_sumsOnlyCollectedForAsset() {
        em.persistAndFlush(basePayment(LocalDate.of(2026, 7, 9))
                .amortizationPer100(new BigDecimal("8.000000")).status(InvestmentPaymentStatus.COBRADO).build());
        em.persistAndFlush(basePayment(LocalDate.of(2027, 1, 9))
                .amortizationPer100(new BigDecimal("8.000000")).status(InvestmentPaymentStatus.COBRADO).build());
        em.persistAndFlush(basePayment(LocalDate.of(2027, 7, 9))
                .amortizationPer100(new BigDecimal("8.000000")).status(InvestmentPaymentStatus.PENDIENTE).build());

        BigDecimal sum = paymentRepository.sumCollectedAmortizationPer100(asset.getId());

        assertThat(sum).isEqualByComparingTo("16.000000");
    }

    @Test
    @DisplayName("sumCollectedAmortizationPer100: 0 cuando el activo no tiene pagos cobrados")
    void sumCollectedAmortizationPer100_returnsZero_whenNoneCollected() {
        BigDecimal sum = paymentRepository.sumCollectedAmortizationPer100(asset.getId());

        assertThat(sum).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("existsByInvestmentAsset_IdAndStatus: true/false según haya filas en ese estado")
    void existsByInvestmentAsset_IdAndStatus_checksExistence() {
        em.persistAndFlush(basePayment(LocalDate.of(2026, 7, 9)).status(InvestmentPaymentStatus.PENDIENTE).build());

        assertThat(paymentRepository.existsByInvestmentAsset_IdAndStatus(asset.getId(), InvestmentPaymentStatus.PENDIENTE)).isTrue();
        assertThat(paymentRepository.existsByInvestmentAsset_IdAndStatus(asset.getId(), InvestmentPaymentStatus.COBRADO)).isFalse();
    }
}
