package com.vectis.backend.repository;

import com.vectis.backend.domain.entity.Account;
import com.vectis.backend.domain.entity.InvestmentAsset;
import com.vectis.backend.domain.entity.InvestmentAssetStatus;
import com.vectis.backend.domain.entity.InvestmentAssetType;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class InvestmentRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private InvestmentRepository investmentRepository;

    private User user;
    private LocalDate purchaseDate;

    @BeforeEach
    void setUp() {
        user = em.persistFlushFind(User.builder()
                .email("user@vectis.com").passwordHash("hash").fullName("Test User").build());
        purchaseDate = LocalDate.of(2026, 7, 1);
    }

    private InvestmentAsset.InvestmentAssetBuilder baseAsset(InvestmentAssetType type) {
        return InvestmentAsset.builder()
                .user(user).name("Activo Test").type(type).currency("ARS")
                .principal(new BigDecimal("100000.0000")).purchaseDate(purchaseDate)
                .tna(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("findAllByTypesAndAutoTrackTrue: sólo activos con autoTrack=true cuyo tipo esté en la lista")
    void findAllByTypesAndAutoTrackTrue_filtersByTypeListAndAutoTrack() {
        em.persistAndFlush(baseAsset(InvestmentAssetType.LETRA).autoTrack(true).build());
        em.persistAndFlush(baseAsset(InvestmentAssetType.BONO).autoTrack(true).build());
        em.persistAndFlush(baseAsset(InvestmentAssetType.ON).autoTrack(false).build());
        em.persistAndFlush(baseAsset(InvestmentAssetType.FCI_CUOTAPARTES).autoTrack(true).build());

        List<InvestmentAsset> result = investmentRepository.findAllByTypesAndAutoTrackTrue(
                List.of(InvestmentAssetType.LETRA, InvestmentAssetType.BONO));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(InvestmentAsset::getType)
                .containsExactlyInAnyOrder(InvestmentAssetType.LETRA, InvestmentAssetType.BONO);
    }

    @Test
    @DisplayName("findFciLinksByUser: sólo activos FCI vinculados a una cuenta, del usuario dado")
    void findFciLinksByUser_returnsOnlyFciTypeLinkedToAccount() {
        Account account = em.persistFlushFind(Account.builder()
                .user(user).name("UALA").kind("Banco").ccy("ARS")
                .balance(new BigDecimal("0.0000")).includeInCashflow(true)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC)).updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());

        em.persistAndFlush(baseAsset(InvestmentAssetType.FCI).account(account).tna(new BigDecimal("35.0000")).build());
        em.persistAndFlush(baseAsset(InvestmentAssetType.LETRA).account(account).build()); // no es FCI, no cuenta
        em.persistAndFlush(baseAsset(InvestmentAssetType.FCI).account(null).build()); // FCI sin cuenta, no cuenta

        List<InvestmentRepository.FciAccountLinkView> result = investmentRepository.findFciLinksByUser(user.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAccountId()).isEqualTo(account.getId());
        assertThat(result.get(0).getTna()).isEqualByComparingTo("35.0000");
    }

    @Test
    @DisplayName("findFciLinksByUser: excluye un FCI ya COBRADA — no debe seguir marcando la cuenta como remunerada")
    void findFciLinksByUser_excludesCollectedFciAsset() {
        Account account = em.persistFlushFind(Account.builder()
                .user(user).name("UALA").kind("Banco").ccy("ARS")
                .balance(new BigDecimal("0.0000")).includeInCashflow(true)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC)).updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());

        em.persistAndFlush(baseAsset(InvestmentAssetType.FCI).account(account)
                .status(InvestmentAssetStatus.COBRADA).build());

        List<InvestmentRepository.FciAccountLinkView> result = investmentRepository.findFciLinksByUser(user.getId());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findFciLinksByUser: si hay una FCI COBRADA y otra ACTIVA en la misma cuenta, sólo trae la ACTIVA")
    void findFciLinksByUser_ignoresCollectedKeepsActive() {
        Account account = em.persistFlushFind(Account.builder()
                .user(user).name("UALA").kind("Banco").ccy("ARS")
                .balance(new BigDecimal("0.0000")).includeInCashflow(true)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC)).updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());

        em.persistAndFlush(baseAsset(InvestmentAssetType.FCI).account(account)
                .status(InvestmentAssetStatus.COBRADA).tna(new BigDecimal("20.0000")).build());
        em.persistAndFlush(baseAsset(InvestmentAssetType.FCI).account(account)
                .status(InvestmentAssetStatus.ACTIVA).tna(new BigDecimal("35.0000")).build());

        List<InvestmentRepository.FciAccountLinkView> result = investmentRepository.findFciLinksByUser(user.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTna()).isEqualByComparingTo("35.0000");
    }

    @Test
    @DisplayName("findTopByAccount_IdAndTypeAndStatusOrderByCreatedAtDesc: excluye una FCI COBRADA para la misma cuenta")
    void findTopByAccountAndTypeAndStatus_excludesCollected() {
        Account account = em.persistFlushFind(Account.builder()
                .user(user).name("UALA").kind("Banco").ccy("ARS")
                .balance(new BigDecimal("0.0000")).includeInCashflow(true)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC)).updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());

        em.persistAndFlush(baseAsset(InvestmentAssetType.FCI).account(account)
                .status(InvestmentAssetStatus.COBRADA).build());

        var result = investmentRepository.findTopByAccount_IdAndTypeAndStatusOrderByCreatedAtDesc(
                account.getId(), InvestmentAssetType.FCI, InvestmentAssetStatus.ACTIVA);

        assertThat(result).isEmpty();
    }
}
