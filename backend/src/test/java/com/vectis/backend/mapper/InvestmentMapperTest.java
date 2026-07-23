package com.vectis.backend.mapper;

import com.vectis.backend.domain.entity.InvestmentAsset;
import com.vectis.backend.domain.entity.InvestmentAssetStatus;
import com.vectis.backend.domain.entity.InvestmentAssetType;
import com.vectis.backend.domain.entity.InvestmentMovement;
import com.vectis.backend.domain.entity.InvestmentMovementType;
import com.vectis.backend.domain.entity.InvestmentPayment;
import com.vectis.backend.domain.entity.InvestmentPaymentSource;
import com.vectis.backend.domain.entity.InvestmentPaymentStatus;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.InvestmentPaymentResponse;
import com.vectis.backend.dto.InvestmentResponse;
import com.vectis.backend.repository.InvestmentPaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("InvestmentMapper")
class InvestmentMapperTest {

    @Mock
    private InvestmentPaymentRepository investmentPaymentRepository;

    private InvestmentMapper mapper;

    private User user;

    @BeforeEach
    void setUp() {
        mapper = new InvestmentMapper(investmentPaymentRepository);
        user = User.builder().id(UUID.randomUUID()).email("u@vectis.com").fullName("U").passwordHash("h").build();
    }

    private InvestmentAsset bondAsset(UUID id) {
        InvestmentAsset asset = InvestmentAsset.builder()
                .id(id).user(user).name("AL30").type(InvestmentAssetType.BONO)
                .currency("ARS").principal(new BigDecimal("100000.0000"))
                .purchaseDate(LocalDate.of(2026, 1, 1)).tna(BigDecimal.ZERO)
                .status(InvestmentAssetStatus.ACTIVA)
                .build();
        asset.getMovements().add(InvestmentMovement.builder()
                .movementDate(LocalDate.of(2026, 1, 1))
                .type(InvestmentMovementType.SUSCRIPCION)
                .amount(new BigDecimal("100000.00")).units(new BigDecimal("100")).build());
        return asset;
    }

    @Test
    @DisplayName("toResponse(asset) para BONO trae sólo las amortizaciones COBRADO con amortizationPer100 > 0")
    void toResponse_forBond_includesOnlyCollectedAmortizationsWithPositiveAmount() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = bondAsset(assetId);

        InvestmentPayment collectedWithAmort = InvestmentPayment.builder()
                .id(UUID.randomUUID()).investmentAsset(asset)
                .cuttingDate(LocalDate.of(2026, 7, 1))
                .rentPer100(new BigDecimal("1.000000"))
                .amortizationPer100(new BigDecimal("8.000000"))
                .currency("ARS")
                .status(InvestmentPaymentStatus.COBRADO)
                .source(InvestmentPaymentSource.PPI)
                .collectedDate(LocalDate.of(2026, 7, 1))
                .residualAfterPer100(new BigDecimal("92.000000"))
                .build();

        InvestmentPayment collectedWithoutAmort = InvestmentPayment.builder()
                .id(UUID.randomUUID()).investmentAsset(asset)
                .cuttingDate(LocalDate.of(2026, 4, 1))
                .rentPer100(new BigDecimal("1.500000"))
                .amortizationPer100(BigDecimal.ZERO)
                .currency("ARS")
                .status(InvestmentPaymentStatus.COBRADO)
                .source(InvestmentPaymentSource.PPI)
                .collectedDate(LocalDate.of(2026, 4, 1))
                .build();

        InvestmentPayment pending = InvestmentPayment.builder()
                .id(UUID.randomUUID()).investmentAsset(asset)
                .cuttingDate(LocalDate.of(2026, 10, 1))
                .rentPer100(new BigDecimal("1.000000"))
                .amortizationPer100(new BigDecimal("10.000000"))
                .currency("ARS")
                .status(InvestmentPaymentStatus.PENDIENTE)
                .source(InvestmentPaymentSource.PPI)
                .build();

        given(investmentPaymentRepository.findAllByInvestmentAsset_IdAndStatusOrderByCuttingDateAsc(
                assetId, InvestmentPaymentStatus.COBRADO))
                .willReturn(List.of(collectedWithAmort, collectedWithoutAmort));

        InvestmentResponse response = mapper.toResponse(asset);

        assertThat(response.collectedAmortizations()).hasSize(1);
        InvestmentPaymentResponse only = response.collectedAmortizations().get(0);
        assertThat(only.collectedDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(only.amortizationPer100()).isEqualByComparingTo("8.000000");
        assertThat(only.residualAfterPer100()).isEqualByComparingTo("92.000000");
        // el mismatch de fecha con la pendiente confirma que sólo se consultó COBRADO
        assertThat(response.collectedAmortizations())
                .noneMatch(p -> p.cuttingDate().equals(pending.getCuttingDate()));
    }

    @Test
    @DisplayName("toResponse(asset) para tipo no BONO/ON no consulta pagos y deja el campo vacío")
    void toResponse_forNonBondType_leavesFieldEmptyAndSkipsQuery() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = InvestmentAsset.builder()
                .id(assetId).user(user).name("Cuotapartes XYZ").type(InvestmentAssetType.FCI_CUOTAPARTES)
                .currency("ARS").principal(new BigDecimal("50000.0000"))
                .purchaseDate(LocalDate.of(2026, 1, 1)).tna(BigDecimal.ZERO)
                .status(InvestmentAssetStatus.ACTIVA)
                .build();

        InvestmentResponse response = mapper.toResponse(asset);

        assertThat(response.collectedAmortizations()).isEmpty();
        assertThat(response.type()).isEqualTo("FCI_CUOTAPARTES");
        verify(investmentPaymentRepository, never())
                .findAllByInvestmentAsset_IdAndStatusOrderByCuttingDateAsc(any(), any());
        verify(investmentPaymentRepository, never())
                .findAllByInvestmentAsset_IdInAndStatusOrderByCuttingDateAsc(any(), any());
    }

    @Test
    @DisplayName("toResponse(asset, payments) variante batch respeta la lista provista sin consultar el repo")
    void toResponse_batchOverload_usesProvidedListWithoutQuerying() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = bondAsset(assetId);

        InvestmentPayment collected = InvestmentPayment.builder()
                .id(UUID.randomUUID()).investmentAsset(asset)
                .cuttingDate(LocalDate.of(2026, 7, 1))
                .amortizationPer100(new BigDecimal("8.000000"))
                .currency("ARS")
                .status(InvestmentPaymentStatus.COBRADO)
                .source(InvestmentPaymentSource.PPI)
                .build();

        InvestmentResponse response = mapper.toResponse(asset, List.of(collected));

        assertThat(response.collectedAmortizations()).hasSize(1);
        verify(investmentPaymentRepository, never())
                .findAllByInvestmentAsset_IdAndStatusOrderByCuttingDateAsc(any(), any());
    }
}
