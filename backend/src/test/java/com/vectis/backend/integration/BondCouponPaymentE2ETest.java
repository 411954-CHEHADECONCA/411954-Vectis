package com.vectis.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.vectis.backend.config.PpiMarketDataClient;
import com.vectis.backend.config.PpiMarketDataClient.PpiBondEstimate;
import com.vectis.backend.config.PpiMarketDataClient.PpiBondFlow;
import com.vectis.backend.domain.entity.Account;
import com.vectis.backend.domain.entity.InvestmentAssetType;
import com.vectis.backend.domain.entity.InvestmentSourceType;
import com.vectis.backend.domain.entity.Transaction;
import com.vectis.backend.domain.entity.TransactionType;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.ConfirmPaymentRequest;
import com.vectis.backend.dto.InvestmentRequest;
import com.vectis.backend.repository.AccountRepository;
import com.vectis.backend.repository.TransactionRepository;
import com.vectis.backend.repository.UserRepository;
import com.vectis.backend.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E2E del flujo completo de cupones de un BONO auto-track: alta → sync del calendario desde PPI
 * (mockeado) → confirmación de un pago → verificación de las Transactions INCOME generadas y de que
 * aparecen en el cashflow del mes. Corre sobre el contexto completo con H2 (perfil {@code test},
 * Flyway off, esquema derivado de las entidades). PPI y JWT se mockean como {@code @MockBean}; el
 * resto de la cadena (seguridad, servicios, persistencia) es real.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Pagos de cupones de BONO — E2E (alta → sync → confirmar → cashflow)")
class BondCouponPaymentE2ETest {

    private static final String TOKEN = "e2e-token";
    private static final String AUTH  = "Bearer e2e-token";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;

    @MockBean private JavaMailSender mailSender;
    @MockBean private PpiMarketDataClient ppiMarketDataClient;
    @MockBean private JwtService jwtService;

    private User user;
    private Account account;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .email("e2e-" + UUID.randomUUID() + "@vectis.test")
                .passwordHash("hash").fullName("E2E User").build());
        account = accountRepository.save(Account.builder()
                .user(user).name("Cuenta USD").kind("Banco").ccy("USD")
                .balance(new BigDecimal("100000.0000")).includeInCashflow(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build());

        // Auth: token fijo → id del usuario persistido (el filtro real resuelve el User vía userRepository).
        given(jwtService.isTokenValid(TOKEN)).willReturn(true);
        given(jwtService.extractUserId(TOKEN)).willReturn(user.getId().toString());

        // PPI: un único flow futuro (residual 64 → no es pago final, el activo no auto-cierra).
        given(ppiMarketDataClient.isConfigured()).willReturn(true);
        PpiBondFlow flow = new PpiBondFlow(
                LocalDate.of(2030, 7, 9), new BigDecimal("0.72"), new BigDecimal("0.27"), new BigDecimal("8"), null);
        PpiBondEstimate estimate = new PpiBondEstimate(List.of(flow), "US$", "Dólar", LocalDate.of(2030, 7, 9));
        given(ppiMarketDataClient.getBondEstimate(eq("AL30"), any())).willReturn(Optional.of(estimate));
    }

    @Test
    @DisplayName("Alta de BONO → sync → confirmar genera renta+amortización y se reflejan en cashflow")
    void fullFlow_createSyncConfirmCashflow() throws Exception {
        // 1. Alta de un BONO auto-track en USD (cuenta de cobro = la misma cuenta USD). La compra va
        //    en el mes actual (abierto) con fondos suficientes en la cuenta (balance 100k > principal).
        InvestmentRequest createReq = new InvestmentRequest(
                "AL30", InvestmentAssetType.BONO, "USD", new BigDecimal("50000.00"),
                LocalDate.now(), null, BigDecimal.ZERO, account.getId(), true, "AL30", true);
        MvcResult created = mockMvc.perform(post("/api/investments")
                        .header(AUTHORIZATION, AUTH).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID assetId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));

        // 2. Sincronizar el calendario desde PPI (idempotente respecto del afterCommit del alta).
        MvcResult synced = mockMvc.perform(post("/api/investments/{id}/payments/sync", assetId)
                        .header(AUTHORIZATION, AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("PENDIENTE"))
                .andExpect(jsonPath("$[0].currency").value("USD"))
                .andReturn();
        UUID paymentId = UUID.fromString(JsonPath.read(synced.getResponse().getContentAsString(), "$[0].id"));

        // 3. Confirmar el cobro hoy (mes actual → abierto): renta 270 + amortización 8000.
        LocalDate today = LocalDate.now();
        ConfirmPaymentRequest confirmReq = new ConfirmPaymentRequest(
                today, new BigDecimal("270.00"), new BigDecimal("8000.00"));
        mockMvc.perform(post("/api/investments/{id}/payments/{pid}/confirm", assetId, paymentId)
                        .header(AUTHORIZATION, AUTH).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionsCreated").value(2))
                .andExpect(jsonPath("$.assetCollected").value(false))
                .andExpect(jsonPath("$.payment.status").value("COBRADO"));

        // 4. Se persistieron dos Transactions INCOME de cobro (renta + amortización) en la cuenta USD
        //    — además de la SUSCRIPCION (EXPENSE) del alta, que se excluye del filtro.
        List<Transaction> assetTxs = transactionRepository.findAll().stream()
                .filter(t -> t.getInvestmentAsset() != null && assetId.equals(t.getInvestmentAsset().getId()))
                .filter(t -> t.getInvestmentSourceType() == InvestmentSourceType.COUPON_RENT
                        || t.getInvestmentSourceType() == InvestmentSourceType.AMORTIZATION)
                .toList();
        assertThat(assetTxs).hasSize(2);
        assertThat(assetTxs).allSatisfy(t -> {
            assertThat(t.getType()).isEqualTo(TransactionType.INCOME);
            assertThat(t.getCcy()).isEqualTo("USD");
            assertThat(t.getAccount().getId()).isEqualTo(account.getId());
            assertThat(t.getTransactionDate()).isEqualTo(today);
        });
        assertThat(assetTxs).anySatisfy(t -> {
            assertThat(t.getInvestmentSourceType()).isEqualTo(InvestmentSourceType.COUPON_RENT);
            assertThat(t.getAmount()).isEqualByComparingTo("270.00");
        });
        assertThat(assetTxs).anySatisfy(t -> {
            assertThat(t.getInvestmentSourceType()).isEqualTo(InvestmentSourceType.AMORTIZATION);
            assertThat(t.getAmount()).isEqualByComparingTo("8000.00");
        });

        // 5. El cashflow del mes muestra las filas de renta y amortización de inversión.
        mockMvc.perform(get("/api/cashflow")
                        .param("year", String.valueOf(today.getYear()))
                        .param("month", String.valueOf(today.getMonthValue()))
                        .header(AUTHORIZATION, AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.income.byCategory[?(@.name=='Renta de inversión (cupones)')]", hasSize(1)))
                .andExpect(jsonPath("$.income.byCategory[?(@.name=='Amortización de inversión')]", hasSize(1)));
    }
}
