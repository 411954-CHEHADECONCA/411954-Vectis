package com.vectis.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vectis.backend.config.SecurityConfig;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.TransferRequest;
import com.vectis.backend.dto.TransferResponse;
import com.vectis.backend.exception.VectisException;
import com.vectis.backend.repository.UserRepository;
import com.vectis.backend.service.JwtService;
import com.vectis.backend.service.TransferService;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransferController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("TransferController")
class TransferControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private TransferService transferService;
    @MockBean private JwtService jwtService;
    @MockBean private UserRepository userRepository;

    private User mockUser;
    private UUID userId;
    private static final String VALID_TOKEN = "valid-test-token";
    private static final String AUTH_HEADER = "Bearer " + VALID_TOKEN;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        mockUser = User.builder()
                .id(userId)
                .email("user@vectis.com")
                .fullName("Test User")
                .passwordHash("hash")
                .build();

        given(jwtService.isTokenValid(VALID_TOKEN)).willReturn(true);
        given(jwtService.extractUserId(VALID_TOKEN)).willReturn(userId.toString());
        given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));
    }

    // ── POST /api/transfers ───────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/transfers sin token retorna 401")
    void create_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/transfers con sourceAccountId nulo retorna 400")
    void create_nullSourceAccountId_returns400() throws Exception {
        TransferRequest bad = new TransferRequest(null, UUID.randomUUID(),
                new BigDecimal("1000"), null, LocalDate.now(), null);

        mockMvc.perform(post("/api/transfers")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/transfers con monto negativo retorna 400")
    void create_negativeAmount_returns400() throws Exception {
        TransferRequest bad = new TransferRequest(UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("-100"), null, LocalDate.now(), null);

        mockMvc.perform(post("/api/transfers")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/transfers con body válido retorna 201 con transferGroupId")
    void create_validRequest_returns201() throws Exception {
        TransferResponse response = buildResponse();
        given(transferService.create(any(TransferRequest.class), any(User.class))).willReturn(response);

        mockMvc.perform(post("/api/transfers")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transferGroupId").value(response.transferGroupId().toString()))
                .andExpect(jsonPath("$.sourceCcy").value("ARS"))
                .andExpect(jsonPath("$.destCcy").value("ARS"));
    }

    @Test
    @DisplayName("POST /api/transfers cuando cuentas son iguales retorna 400")
    void create_sameAccount_returns400() throws Exception {
        given(transferService.create(any(TransferRequest.class), any(User.class)))
                .willThrow(new VectisException("La cuenta origen y destino no pueden ser la misma", HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/transfers")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/transfers cuando cuenta no existe retorna 404")
    void create_accountNotFound_returns404() throws Exception {
        given(transferService.create(any(TransferRequest.class), any(User.class)))
                .willThrow(new VectisException("Cuenta no encontrada", HttpStatus.NOT_FOUND));

        mockMvc.perform(post("/api/transfers")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /api/transfers/{transferGroupId} ───────────────────────────────

    @Test
    @DisplayName("DELETE /api/transfers/{id} sin token retorna 401")
    void delete_withoutToken_returns401() throws Exception {
        mockMvc.perform(delete("/api/transfers/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /api/transfers/{id} válido retorna 204")
    void delete_validGroupId_returns204() throws Exception {
        doNothing().when(transferService).delete(any(UUID.class), any(User.class));

        mockMvc.perform(delete("/api/transfers/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/transfers/{id} no encontrado retorna 404")
    void delete_unknownGroupId_returns404() throws Exception {
        doThrow(new VectisException("Transferencia no encontrada", HttpStatus.NOT_FOUND))
                .when(transferService).delete(any(UUID.class), any(User.class));

        mockMvc.perform(delete("/api/transfers/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isNotFound());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private TransferRequest validRequest() {
        return new TransferRequest(UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("10000"), null, LocalDate.of(2026, 6, 21), "Recarga");
    }

    private TransferResponse buildResponse() {
        UUID groupId = UUID.randomUUID();
        UUID srcId   = UUID.randomUUID();
        UUID dstId   = UUID.randomUUID();
        return new TransferResponse(
                groupId, UUID.randomUUID(), UUID.randomUUID(),
                srcId, "Galicia ARS",
                dstId, "Mercado Pago",
                new BigDecimal("10000.0000"), "ARS",
                new BigDecimal("10000.0000"), "ARS",
                LocalDate.of(2026, 6, 21),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }
}
