package com.vectis.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vectis.backend.config.SecurityConfig;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.ConfirmPaymentRequest;
import com.vectis.backend.dto.ConfirmPaymentResponse;
import com.vectis.backend.dto.InvestmentPaymentRequest;
import com.vectis.backend.dto.InvestmentPaymentResponse;
import com.vectis.backend.dto.InvestmentPaymentUpdateRequest;
import com.vectis.backend.dto.PendingPaymentResponse;
import com.vectis.backend.exception.InvestmentNotFoundException;
import com.vectis.backend.exception.InvestmentPaymentNotFoundException;
import com.vectis.backend.exception.VectisException;
import com.vectis.backend.repository.UserRepository;
import com.vectis.backend.service.InvestmentPaymentService;
import com.vectis.backend.service.InvestmentPaymentSyncService;
import com.vectis.backend.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InvestmentPaymentController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("InvestmentPaymentController")
class InvestmentPaymentControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private InvestmentPaymentService     investmentPaymentService;
    @MockBean private InvestmentPaymentSyncService investmentPaymentSyncService;
    @MockBean private JwtService                jwtService;
    @MockBean private UserRepository            userRepository;

    private User mockUser;
    private UUID userId;
    private UUID assetId;
    private UUID paymentId;
    private static final String VALID_TOKEN = "valid-test-token";
    private static final String AUTH_HEADER = "Bearer " + VALID_TOKEN;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        assetId = UUID.randomUUID();
        paymentId = UUID.randomUUID();
        mockUser = User.builder()
                .id(userId).email("user@vectis.com").fullName("Test User").passwordHash("hash").build();

        given(jwtService.isTokenValid(VALID_TOKEN)).willReturn(true);
        given(jwtService.extractUserId(VALID_TOKEN)).willReturn(userId.toString());
        given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));
    }

    private InvestmentPaymentResponse buildPaymentResponse() {
        return InvestmentPaymentResponse.builder()
                .id(paymentId)
                .cuttingDate(LocalDate.of(2026, 7, 9))
                .rentPer100(new BigDecimal("0.270000"))
                .amortizationPer100(new BigDecimal("8.000000"))
                .estimatedRentAmount(new BigDecimal("270.0000"))
                .estimatedAmortizationAmount(new BigDecimal("8000.0000"))
                .currency("USD")
                .status("PENDIENTE")
                .source("PPI")
                .userEdited(false)
                .residualAfterPer100(new BigDecimal("64.000000"))
                .build();
    }

    // ─── GET /{id}/payments ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /{id}/payments sin token retorna 401")
    void getPayments_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/investments/{id}/payments", assetId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /{id}/payments con token retorna 200 y la lista")
    void getPayments_withToken_returns200() throws Exception {
        given(investmentPaymentService.getPayments(assetId, mockUser)).willReturn(List.of(buildPaymentResponse()));

        mockMvc.perform(get("/api/investments/{id}/payments", assetId)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currency").value("USD"))
                .andExpect(jsonPath("$[0].status").value("PENDIENTE"));
    }

    @Test
    @DisplayName("GET /{id}/payments activo inexistente retorna 404")
    void getPayments_assetNotFound_returns404() throws Exception {
        given(investmentPaymentService.getPayments(assetId, mockUser))
                .willThrow(new InvestmentNotFoundException(assetId));

        mockMvc.perform(get("/api/investments/{id}/payments", assetId)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isNotFound());
    }

    // ─── POST /{id}/payments ─────────────────────────────────────────────────

    @Test
    @DisplayName("POST /{id}/payments con body válido retorna 201")
    void createPayment_validRequest_returns201() throws Exception {
        InvestmentPaymentRequest request = new InvestmentPaymentRequest(
                LocalDate.of(2026, 12, 1), "USD", new BigDecimal("100.00"), new BigDecimal("500.00"));
        given(investmentPaymentService.createManualPayment(any(), any(InvestmentPaymentRequest.class), any(User.class)))
                .willReturn(buildPaymentResponse());

        mockMvc.perform(post("/api/investments/{id}/payments", assetId)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    @DisplayName("POST /{id}/payments sin cuttingDate retorna 400")
    void createPayment_missingCuttingDate_returns400() throws Exception {
        String body = """
            {"currency": "USD", "rentAmount": 100.00, "amortizationAmount": 500.00}
            """;

        mockMvc.perform(post("/api/investments/{id}/payments", assetId)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /{id}/payments duplicado retorna 409")
    void createPayment_duplicate_returns409() throws Exception {
        InvestmentPaymentRequest request = new InvestmentPaymentRequest(
                LocalDate.of(2026, 12, 1), "USD", null, null);
        given(investmentPaymentService.createManualPayment(any(), any(InvestmentPaymentRequest.class), any(User.class)))
                .willThrow(new VectisException("Ya existe un pago manual registrado para la fecha", HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/investments/{id}/payments", assetId)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    // ─── PUT /{id}/payments/{paymentId} ──────────────────────────────────────

    @Test
    @DisplayName("PUT /{id}/payments/{paymentId} con body vacío (todo opcional) retorna 200")
    void updatePayment_emptyBody_returns200() throws Exception {
        InvestmentPaymentUpdateRequest request = new InvestmentPaymentUpdateRequest(null, null, null);
        given(investmentPaymentService.updatePayment(any(), any(), any(InvestmentPaymentUpdateRequest.class), any(User.class)))
                .willReturn(buildPaymentResponse());

        mockMvc.perform(put("/api/investments/{id}/payments/{paymentId}", assetId, paymentId)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /{id}/payments/{paymentId} pago no pendiente retorna 409")
    void updatePayment_notPending_returns409() throws Exception {
        InvestmentPaymentUpdateRequest request = new InvestmentPaymentUpdateRequest(null, new BigDecimal("10"), null);
        given(investmentPaymentService.updatePayment(any(), any(), any(InvestmentPaymentUpdateRequest.class), any(User.class)))
                .willThrow(new VectisException("El pago no está pendiente", HttpStatus.CONFLICT));

        mockMvc.perform(put("/api/investments/{id}/payments/{paymentId}", assetId, paymentId)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    // ─── DELETE /{id}/payments/{paymentId} ───────────────────────────────────

    @Test
    @DisplayName("DELETE /{id}/payments/{paymentId} manual pendiente retorna 204")
    void deletePayment_manualPending_returns204() throws Exception {
        mockMvc.perform(delete("/api/investments/{id}/payments/{paymentId}", assetId, paymentId)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /{id}/payments/{paymentId} no manual/pendiente retorna 409")
    void deletePayment_conflict_returns409() throws Exception {
        doThrow(new VectisException("Sólo se puede eliminar un pago manual pendiente", HttpStatus.CONFLICT))
                .when(investmentPaymentService).deletePayment(assetId, paymentId, mockUser);

        mockMvc.perform(delete("/api/investments/{id}/payments/{paymentId}", assetId, paymentId)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isConflict());
    }

    // ─── POST /{id}/payments/{paymentId}/omit ────────────────────────────────

    @Test
    @DisplayName("POST /{id}/payments/{paymentId}/omit retorna 200 con estado OMITIDO")
    void omitPayment_returns200() throws Exception {
        given(investmentPaymentService.omitPayment(assetId, paymentId, mockUser))
                .willReturn(InvestmentPaymentResponse.builder().id(paymentId).status("OMITIDO").build());

        mockMvc.perform(post("/api/investments/{id}/payments/{paymentId}/omit", assetId, paymentId)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OMITIDO"));
    }

    @Test
    @DisplayName("POST /{id}/payments/{paymentId}/omit pago inexistente retorna 404")
    void omitPayment_notFound_returns404() throws Exception {
        given(investmentPaymentService.omitPayment(assetId, paymentId, mockUser))
                .willThrow(new InvestmentPaymentNotFoundException(paymentId));

        mockMvc.perform(post("/api/investments/{id}/payments/{paymentId}/omit", assetId, paymentId)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isNotFound());
    }

    // ─── POST /{id}/payments/{paymentId}/confirm ─────────────────────────────

    @Test
    @DisplayName("POST /{id}/payments/{paymentId}/confirm con body válido retorna 200")
    void confirmPayment_validRequest_returns200() throws Exception {
        ConfirmPaymentRequest request = new ConfirmPaymentRequest(
                LocalDate.of(2026, 7, 9), new BigDecimal("270.00"), new BigDecimal("8000.00"));
        ConfirmPaymentResponse response = ConfirmPaymentResponse.builder()
                .payment(buildPaymentResponse()).transactionsCreated(2).assetCollected(false).build();
        given(investmentPaymentService.confirmPayment(any(), any(), any(ConfirmPaymentRequest.class), any(User.class)))
                .willReturn(response);

        mockMvc.perform(post("/api/investments/{id}/payments/{paymentId}/confirm", assetId, paymentId)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionsCreated").value(2))
                .andExpect(jsonPath("$.assetCollected").value(false));
    }

    @Test
    @DisplayName("POST /{id}/payments/{paymentId}/confirm sin collectedDate retorna 400")
    void confirmPayment_missingCollectedDate_returns400() throws Exception {
        String body = """
            {"rentAmount": 270.00, "amortizationAmount": 8000.00}
            """;

        mockMvc.perform(post("/api/investments/{id}/payments/{paymentId}/confirm", assetId, paymentId)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /{id}/payments/{paymentId}/confirm mes cerrado retorna 409")
    void confirmPayment_monthClosed_returns409() throws Exception {
        ConfirmPaymentRequest request = new ConfirmPaymentRequest(
                LocalDate.of(2026, 7, 9), BigDecimal.ZERO, BigDecimal.ZERO);
        given(investmentPaymentService.confirmPayment(any(), any(), any(ConfirmPaymentRequest.class), any(User.class)))
                .willThrow(new VectisException("No se puede confirmar: el mes elegido ya está cerrado", HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/investments/{id}/payments/{paymentId}/confirm", assetId, paymentId)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    // ─── POST /{id}/payments/{paymentId}/revert ──────────────────────────────

    @Test
    @DisplayName("POST /{id}/payments/{paymentId}/revert retorna 200 con estado PENDIENTE")
    void revertPayment_returns200() throws Exception {
        given(investmentPaymentService.revertPayment(assetId, paymentId, mockUser))
                .willReturn(InvestmentPaymentResponse.builder().id(paymentId).status("PENDIENTE").build());

        mockMvc.perform(post("/api/investments/{id}/payments/{paymentId}/revert", assetId, paymentId)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDIENTE"));
    }

    // ─── POST /{id}/payments/sync ─────────────────────────────────────────────

    @Test
    @DisplayName("POST /{id}/payments/sync siempre retorna 200 (incluso si PPI degrada)")
    void syncPayments_returns200() throws Exception {
        given(investmentPaymentSyncService.syncSchedule(assetId, mockUser)).willReturn(List.of(buildPaymentResponse()));

        mockMvc.perform(post("/api/investments/{id}/payments/sync", assetId)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currency").value("USD"));
    }

    // ─── GET /payments/pending ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /payments/pending sin token retorna 401")
    void getPendingPayments_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/investments/payments/pending"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /payments/pending con token retorna 200 y la lista")
    void getPendingPayments_withToken_returns200() throws Exception {
        PendingPaymentResponse pending = PendingPaymentResponse.builder()
                .paymentId(paymentId).assetId(assetId).assetName("AL30")
                .cuttingDate(LocalDate.of(2026, 7, 1)).currency("USD")
                .estimatedRent(new BigDecimal("270.00")).estimatedAmortization(new BigDecimal("8000.00"))
                .build();
        given(investmentPaymentService.getPendingPayments(mockUser)).willReturn(List.of(pending));

        mockMvc.perform(get("/api/investments/payments/pending")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].assetName").value("AL30"));
    }
}
