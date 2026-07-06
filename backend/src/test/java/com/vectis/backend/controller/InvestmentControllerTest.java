package com.vectis.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vectis.backend.config.PpiMarketDataClient;
import com.vectis.backend.config.SecurityConfig;
import com.vectis.backend.domain.entity.InvestmentMovementType;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.domain.entity.InvestmentAssetType;
import com.vectis.backend.dto.InvestmentCollectPreviewResponse;
import com.vectis.backend.dto.InvestmentCollectRequest;
import com.vectis.backend.dto.InvestmentCollectResponse;
import com.vectis.backend.dto.InvestmentMovementRequest;
import com.vectis.backend.dto.InvestmentMovementUpdateRequest;
import com.vectis.backend.dto.InvestmentRequest;
import com.vectis.backend.dto.InvestmentResponse;
import com.vectis.backend.dto.InvestmentValuationRequest;
import com.vectis.backend.exception.InvestmentDeleteBlockedException;
import com.vectis.backend.exception.InvestmentMovementNotFoundException;
import com.vectis.backend.exception.InvestmentNotFoundException;
import com.vectis.backend.exception.InvestmentValuationNotFoundException;
import com.vectis.backend.exception.VectisException;
import com.vectis.backend.dto.FciFundDto;
import com.vectis.backend.dto.InstrumentDto;
import com.vectis.backend.repository.UserRepository;
import com.vectis.backend.service.FciValuationSyncService;
import com.vectis.backend.service.PpiValuationSyncService;
import com.vectis.backend.service.InvestmentService;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InvestmentController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("InvestmentController")
class InvestmentControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private InvestmentService        investmentService;
    @MockBean private FciValuationSyncService  fciValuationSyncService;
    @MockBean private PpiValuationSyncService  ppiValuationSyncService;
    @MockBean private PpiMarketDataClient      ppiMarketDataClient;
    @MockBean private JwtService                jwtService;
    @MockBean private UserRepository            userRepository;

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

    // ─── GET /api/investments ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/investments sin token retorna 401")
    void getInvestments_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/investments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/investments con token retorna 200 y la lista")
    void getInvestments_withToken_returns200WithList() throws Exception {
        InvestmentResponse response = buildResponse(UUID.randomUUID());
        given(investmentService.getInvestments(userId)).willReturn(List.of(response));

        mockMvc.perform(get("/api/investments")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("LECAP S31G5"))
                .andExpect(jsonPath("$[0].type").value("LETRA"));
    }

    // ─── POST /api/investments ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/investments con body válido retorna 201 y el activo creado")
    void createInvestment_validRequest_returns201() throws Exception {
        InvestmentRequest request  = buildRequest();
        InvestmentResponse response = buildResponse(UUID.randomUUID());

        given(investmentService.createInvestment(any(InvestmentRequest.class), any(User.class)))
                .willReturn(response);

        mockMvc.perform(post("/api/investments")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("LECAP S31G5"));
    }

    @Test
    @DisplayName("POST /api/investments sin name retorna 400")
    void createInvestment_blankName_returns400() throws Exception {
        InvestmentRequest request = new InvestmentRequest(
                "", InvestmentAssetType.LETRA, "ARS",
                new BigDecimal("1000000.00"),
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 8, 31),
                new BigDecimal("65.00"),
                null,
                false,
                null,
                null);

        mockMvc.perform(post("/api/investments")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/investments con principal negativo retorna 400")
    void createInvestment_negativePrincipal_returns400() throws Exception {
        InvestmentRequest request = new InvestmentRequest(
                "LECAP S31G5", InvestmentAssetType.LETRA, "ARS",
                new BigDecimal("-500.00"),
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 8, 31),
                new BigDecimal("65.00"),
                null,
                false,
                null,
                null);

        mockMvc.perform(post("/api/investments")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/investments con autoTrack=true y sin externalId retorna 400")
    void createInvestment_autoTrackWithoutExternalId_returns400() throws Exception {
        InvestmentRequest request = new InvestmentRequest(
                "FCI Cocos", InvestmentAssetType.FCI_CUOTAPARTES, "ARS",
                BigDecimal.ZERO,
                LocalDate.of(2026, 1, 1),
                null,
                null,
                null,
                true,   // autoTrack activado
                null,   // externalId ausente — debe fallar
                null);

        mockMvc.perform(post("/api/investments")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/investments con autoTrack=true y externalId presente retorna 201")
    void createInvestment_autoTrackWithExternalId_returns201() throws Exception {
        InvestmentRequest request = new InvestmentRequest(
                "FCI Cocos", InvestmentAssetType.FCI_CUOTAPARTES, "ARS",
                BigDecimal.ZERO,
                LocalDate.of(2026, 1, 1),
                null,
                null,
                null,
                true,
                "Cocos Capital - Clase A",
                null);

        InvestmentResponse response = buildResponse(UUID.randomUUID());
        given(investmentService.createInvestment(any(InvestmentRequest.class), any(User.class)))
                .willReturn(response);

        mockMvc.perform(post("/api/investments")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    // ─── PUT /api/investments/{id} ────────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/investments/{id} con body válido retorna 200")
    void updateInvestment_validRequest_returns200() throws Exception {
        UUID               id       = UUID.randomUUID();
        InvestmentResponse response = buildResponse(id);

        given(investmentService.updateInvestment(eq(id), any(InvestmentRequest.class), any(User.class)))
                .willReturn(response);

        mockMvc.perform(put("/api/investments/" + id)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    @DisplayName("PUT /api/investments/{id} cuando el activo no pertenece al usuario retorna 404")
    void updateInvestment_notOwner_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        given(investmentService.updateInvestment(eq(id), any(InvestmentRequest.class), any(User.class)))
                .willThrow(new InvestmentNotFoundException(id));

        mockMvc.perform(put("/api/investments/" + id)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isNotFound());
    }

    // ─── DELETE /api/investments/{id} ─────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/investments/{id} propio retorna 204")
    void deleteInvestment_ownAsset_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(investmentService).deleteInvestment(eq(id), any(User.class));

        mockMvc.perform(delete("/api/investments/" + id)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/investments/{id} no encontrado retorna 404")
    void deleteInvestment_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new InvestmentNotFoundException(id))
                .when(investmentService).deleteInvestment(eq(id), any(User.class));

        mockMvc.perform(delete("/api/investments/" + id)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/investments/{id} con movimientos en mes cerrado retorna 409")
    void deleteInvestment_blockedByClosedMonth_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new InvestmentDeleteBlockedException(id))
                .when(investmentService).deleteInvestment(eq(id), any(User.class));

        mockMvc.perform(delete("/api/investments/" + id)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isConflict());
    }

    // ─── GET /api/investments/{id}/collect-preview ────────────────────────────

    @Test
    @DisplayName("GET /{id}/collect-preview sin token retorna 401")
    void previewCollect_withoutToken_returns401() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(get("/api/investments/" + id + "/collect-preview")
                        .param("date", "2026-07-05"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /{id}/collect-preview con token retorna 200 y el desglose")
    void previewCollect_validRequest_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        InvestmentCollectPreviewResponse response = new InvestmentCollectPreviewResponse(
                new BigDecimal("1000000.0000"), new BigDecimal("100000.0000"),
                new BigDecimal("1100000.0000"), "ARS", true);

        given(investmentService.previewCollect(eq(id), eq(LocalDate.of(2026, 7, 5)), any(User.class)))
                .willReturn(response);

        mockMvc.perform(get("/api/investments/" + id + "/collect-preview")
                        .param("date", "2026-07-05")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capital").value(1000000.0000))
                .andExpect(jsonPath("$.rendimiento").value(100000.0000))
                .andExpect(jsonPath("$.total").value(1100000.0000))
                .andExpect(jsonPath("$.editableRendimiento").value(true));
    }

    @Test
    @DisplayName("GET /{id}/collect-preview cuando el activo no existe retorna 404")
    void previewCollect_assetNotFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        given(investmentService.previewCollect(eq(id), any(LocalDate.class), any(User.class)))
                .willThrow(new InvestmentNotFoundException(id));

        mockMvc.perform(get("/api/investments/" + id + "/collect-preview")
                        .param("date", "2026-07-05")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isNotFound());
    }

    // ─── POST /api/investments/{id}/collect ───────────────────────────────────

    @Test
    @DisplayName("POST /{id}/collect sin token retorna 401")
    void collectInvestment_withoutToken_returns401() throws Exception {
        UUID id = UUID.randomUUID();
        InvestmentCollectRequest req = new InvestmentCollectRequest(LocalDate.of(2026, 7, 5), null);

        mockMvc.perform(post("/api/investments/" + id + "/collect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /{id}/collect con body válido retorna 200 y el resultado del cobro")
    void collectInvestment_validRequest_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        InvestmentCollectRequest req = new InvestmentCollectRequest(LocalDate.of(2026, 7, 5), null);
        InvestmentCollectResponse response = new InvestmentCollectResponse(
                id, new BigDecimal("1100000.0000"), "ARS", true,
                new BigDecimal("1000000.0000"), new BigDecimal("100000.0000"),
                LocalDate.of(2026, 7, 5), "COBRADA");

        given(investmentService.collectInvestment(eq(id), any(InvestmentCollectRequest.class), any(User.class)))
                .willReturn(response);

        mockMvc.perform(post("/api/investments/" + id + "/collect")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.investmentId").value(id.toString()))
                .andExpect(jsonPath("$.currency").value("ARS"))
                .andExpect(jsonPath("$.transactionCreated").value(true))
                .andExpect(jsonPath("$.status").value("COBRADA"));
    }

    @Test
    @DisplayName("POST /{id}/collect sin collectDate en el body retorna 400")
    void collectInvestment_missingCollectDate_returns400() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/investments/" + id + "/collect")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /{id}/collect cuando el activo no existe retorna 404")
    void collectInvestment_assetNotFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        InvestmentCollectRequest req = new InvestmentCollectRequest(LocalDate.of(2026, 7, 5), null);
        given(investmentService.collectInvestment(eq(id), any(InvestmentCollectRequest.class), any(User.class)))
                .willThrow(new InvestmentNotFoundException(id));

        mockMvc.perform(post("/api/investments/" + id + "/collect")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /{id}/collect cuando el mes elegido está cerrado o el activo ya fue cobrado retorna 409")
    void collectInvestment_conflict_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        InvestmentCollectRequest req = new InvestmentCollectRequest(LocalDate.of(2026, 2, 1), null);
        given(investmentService.collectInvestment(eq(id), any(InvestmentCollectRequest.class), any(User.class)))
                .willThrow(new VectisException("No se puede cobrar: el mes elegido ya está cerrado", HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/investments/" + id + "/collect")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /{id}/collect con rendimiento override negativo retorna 422")
    void collectInvestment_negativeOverride_returns422() throws Exception {
        UUID id = UUID.randomUUID();
        InvestmentCollectRequest req = new InvestmentCollectRequest(LocalDate.of(2026, 7, 5), new BigDecimal("-1.00"));
        given(investmentService.collectInvestment(eq(id), any(InvestmentCollectRequest.class), any(User.class)))
                .willThrow(new VectisException("El rendimiento no puede ser negativo", HttpStatus.UNPROCESSABLE_ENTITY));

        mockMvc.perform(post("/api/investments/" + id + "/collect")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }

    // ─── POST /api/investments/{id}/movements ─────────────────────────────────

    @Test
    @DisplayName("POST /{id}/movements sin token retorna 401")
    void addMovement_withoutToken_returns401() throws Exception {
        UUID id = UUID.randomUUID();
        InvestmentMovementRequest req = buildMovementRequest(InvestmentMovementType.SUSCRIPCION, "500000.00");

        mockMvc.perform(post("/api/investments/" + id + "/movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /{id}/movements con body válido retorna 201 y el activo actualizado")
    void addMovement_validRequest_returns201() throws Exception {
        UUID               id       = UUID.randomUUID();
        InvestmentResponse response = buildFciResponse(id);
        InvestmentMovementRequest req = buildMovementRequest(InvestmentMovementType.SUSCRIPCION, "500000.00");

        given(investmentService.addMovement(eq(id), any(InvestmentMovementRequest.class), any(User.class)))
                .willReturn(response);

        mockMvc.perform(post("/api/investments/" + id + "/movements")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("FCI"))
                .andExpect(jsonPath("$.movements").isArray());
    }

    @Test
    @DisplayName("POST /{id}/movements con monto cero retorna 400")
    void addMovement_zeroAmount_returns400() throws Exception {
        UUID id = UUID.randomUUID();
        InvestmentMovementRequest req = buildMovementRequest(InvestmentMovementType.SUSCRIPCION, "0.00");

        mockMvc.perform(post("/api/investments/" + id + "/movements")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /{id}/movements cuando activo no existe retorna 404")
    void addMovement_assetNotFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        InvestmentMovementRequest req = buildMovementRequest(InvestmentMovementType.SUSCRIPCION, "500000.00");

        given(investmentService.addMovement(eq(id), any(InvestmentMovementRequest.class), any(User.class)))
                .willThrow(new InvestmentNotFoundException(id));

        mockMvc.perform(post("/api/investments/" + id + "/movements")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /{id}/movements rescate que supera el saldo retorna 422")
    void addMovement_rescateExceedsBalance_returns422() throws Exception {
        UUID id = UUID.randomUUID();
        InvestmentMovementRequest req = buildMovementRequest(InvestmentMovementType.RESCATE, "999999.00");

        given(investmentService.addMovement(eq(id), any(InvestmentMovementRequest.class), any(User.class)))
                .willThrow(new VectisException("El rescate supera el saldo disponible del fondo",
                        HttpStatus.UNPROCESSABLE_ENTITY));

        mockMvc.perform(post("/api/investments/" + id + "/movements")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }

    // ─── DELETE /api/investments/{id}/movements/{movId} ───────────────────────

    @Test
    @DisplayName("DELETE /{id}/movements/{movId} sin token retorna 401")
    void deleteMovement_withoutToken_returns401() throws Exception {
        UUID id    = UUID.randomUUID();
        UUID movId = UUID.randomUUID();

        mockMvc.perform(delete("/api/investments/" + id + "/movements/" + movId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /{id}/movements/{movId} con token retorna 200 y el activo actualizado")
    void deleteMovement_validRequest_returns200() throws Exception {
        UUID               id       = UUID.randomUUID();
        UUID               movId    = UUID.randomUUID();
        InvestmentResponse response = buildFciResponse(id);

        given(investmentService.deleteMovement(eq(id), eq(movId), any(User.class)))
                .willReturn(response);

        mockMvc.perform(delete("/api/investments/" + id + "/movements/" + movId)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FCI"));
    }

    @Test
    @DisplayName("DELETE /{id}/movements/{movId} cuando movimiento no existe retorna 404")
    void deleteMovement_movementNotFound_returns404() throws Exception {
        UUID id    = UUID.randomUUID();
        UUID movId = UUID.randomUUID();

        given(investmentService.deleteMovement(eq(id), eq(movId), any(User.class)))
                .willThrow(new InvestmentMovementNotFoundException(movId));

        mockMvc.perform(delete("/api/investments/" + id + "/movements/" + movId)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /{id}/movements/{movId} cuando activo no existe retorna 404")
    void deleteMovement_assetNotFound_returns404() throws Exception {
        UUID id    = UUID.randomUUID();
        UUID movId = UUID.randomUUID();

        given(investmentService.deleteMovement(eq(id), eq(movId), any(User.class)))
                .willThrow(new InvestmentNotFoundException(id));

        mockMvc.perform(delete("/api/investments/" + id + "/movements/" + movId)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isNotFound());
    }

    // ─── PUT /api/investments/{id}/movements/{movId} ──────────────────────────

    @Test
    @DisplayName("PUT /{id}/movements/{movId} sin token retorna 401")
    void updateMovement_withoutToken_returns401() throws Exception {
        UUID id    = UUID.randomUUID();
        UUID movId = UUID.randomUUID();
        InvestmentMovementUpdateRequest req =
                new InvestmentMovementUpdateRequest(null, new BigDecimal("12500.00"));

        mockMvc.perform(put("/api/investments/" + id + "/movements/" + movId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /{id}/movements/{movId} con body válido retorna 200 y el activo actualizado")
    void updateMovement_validRequest_returns200() throws Exception {
        UUID               id       = UUID.randomUUID();
        UUID               movId    = UUID.randomUUID();
        InvestmentResponse response = buildFciResponse(id);
        InvestmentMovementUpdateRequest req =
                new InvestmentMovementUpdateRequest(null, new BigDecimal("12500.00"));

        given(investmentService.updateMovement(eq(id), eq(movId),
                any(InvestmentMovementUpdateRequest.class), any(User.class)))
                .willReturn(response);

        mockMvc.perform(put("/api/investments/" + id + "/movements/" + movId)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FCI"));
    }

    @Test
    @DisplayName("PUT /{id}/movements/{movId} sobre un activo no-FCI retorna 409")
    void updateMovement_nonFci_returns409() throws Exception {
        UUID id    = UUID.randomUUID();
        UUID movId = UUID.randomUUID();
        InvestmentMovementUpdateRequest req =
                new InvestmentMovementUpdateRequest(null, new BigDecimal("12500.00"));

        given(investmentService.updateMovement(eq(id), eq(movId),
                any(InvestmentMovementUpdateRequest.class), any(User.class)))
                .willThrow(new VectisException(
                        "Solo se puede editar el interés de los tramos de una Cuenta Remunerada (FCI)",
                        HttpStatus.CONFLICT));

        mockMvc.perform(put("/api/investments/" + id + "/movements/" + movId)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PUT /{id}/movements/{movId} cuando movimiento no existe retorna 404")
    void updateMovement_movementNotFound_returns404() throws Exception {
        UUID id    = UUID.randomUUID();
        UUID movId = UUID.randomUUID();
        InvestmentMovementUpdateRequest req =
                new InvestmentMovementUpdateRequest(new BigDecimal("20000.00"), null);

        given(investmentService.updateMovement(eq(id), eq(movId),
                any(InvestmentMovementUpdateRequest.class), any(User.class)))
                .willThrow(new InvestmentMovementNotFoundException(movId));

        mockMvc.perform(put("/api/investments/" + id + "/movements/" + movId)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private InvestmentRequest buildRequest() {
        return new InvestmentRequest(
                "LECAP S31G5", InvestmentAssetType.LETRA, "ARS",
                new BigDecimal("1000000.00"),
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 8, 31),
                new BigDecimal("65.00"),
                null,
                false,
                null,
                null);
    }

    private InvestmentMovementRequest buildMovementRequest(InvestmentMovementType type, String amount) {
        return new InvestmentMovementRequest(
                LocalDate.of(2026, 1, 1),
                type,
                new BigDecimal(amount),
                null,
                null);
    }

    private InvestmentResponse buildResponse(UUID id) {
        return InvestmentResponse.builder()
                .id(id)
                .name("LECAP S31G5")
                .type("LETRA")
                .currency("ARS")
                .principal(new BigDecimal("1000000.0000"))
                .purchaseDate(LocalDate.of(2026, 1, 15))
                .maturityDate(LocalDate.of(2026, 8, 31))
                .tna(new BigDecimal("65.0000"))
                .accountId(null)
                .accountName(null)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .autoTrack(false)
                .externalId(null)
                .movements(List.of())
                .valuations(List.of())
                .build();
    }

    private InvestmentResponse buildFciResponse(UUID id) {
        return InvestmentResponse.builder()
                .id(id)
                .name("FCI Ahorro")
                .type("FCI")
                .currency("ARS")
                .principal(new BigDecimal("500000.0000"))
                .purchaseDate(LocalDate.of(2026, 1, 1))
                .maturityDate(null)
                .tna(new BigDecimal("47.0000"))
                .accountId(null)
                .accountName(null)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .autoTrack(false)
                .externalId(null)
                .movements(List.of())
                .valuations(List.of())
                .build();
    }

    private InvestmentResponse buildFciCuotapartesResponse(UUID id) {
        return InvestmentResponse.builder()
                .id(id)
                .name("FCI Cuotapartes Test")
                .type("FCI_CUOTAPARTES")
                .currency("ARS")
                .principal(new BigDecimal("0.0000"))
                .purchaseDate(LocalDate.of(2026, 1, 1))
                .maturityDate(null)
                .tna(new BigDecimal("0.0000"))
                .accountId(null)
                .accountName(null)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .autoTrack(false)
                .externalId(null)
                .movements(List.of())
                .valuations(List.of())
                .build();
    }

    private InvestmentValuationRequest buildValuationRequest() {
        return new InvestmentValuationRequest(
                LocalDate.of(2026, 6, 1),
                new BigDecimal("1500.00"));
    }

    // ─── POST /api/investments/{id}/valuations ────────────────────────────────

    @Test
    @DisplayName("POST /{id}/valuations con body válido retorna 201 y el activo actualizado")
    void addValuation_validRequest_returns201() throws Exception {
        UUID               id       = UUID.randomUUID();
        InvestmentResponse response = buildFciCuotapartesResponse(id);

        given(investmentService.addValuation(eq(id), any(InvestmentValuationRequest.class), any(User.class)))
                .willReturn(response);

        mockMvc.perform(post("/api/investments/" + id + "/valuations")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildValuationRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("FCI_CUOTAPARTES"))
                .andExpect(jsonPath("$.valuations").isArray());
    }

    @Test
    @DisplayName("POST /{id}/valuations con precio cero retorna 400")
    void addValuation_zeroPricePerUnit_returns400() throws Exception {
        UUID id = UUID.randomUUID();
        InvestmentValuationRequest req = new InvestmentValuationRequest(
                LocalDate.of(2026, 6, 1), new BigDecimal("0.00"));

        mockMvc.perform(post("/api/investments/" + id + "/valuations")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /{id}/valuations sin token retorna 401")
    void addValuation_withoutToken_returns401() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/investments/" + id + "/valuations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildValuationRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /{id}/valuations cuando el activo no existe retorna 404")
    void addValuation_assetNotFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();

        given(investmentService.addValuation(eq(id), any(InvestmentValuationRequest.class), any(User.class)))
                .willThrow(new InvestmentNotFoundException(id));

        mockMvc.perform(post("/api/investments/" + id + "/valuations")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildValuationRequest())))
                .andExpect(status().isNotFound());
    }

    // ─── PUT /api/investments/{id}/valuations/{valId} ─────────────────────────

    @Test
    @DisplayName("PUT /{id}/valuations/{valId} con body válido retorna 200")
    void updateValuation_validRequest_returns200() throws Exception {
        UUID               id       = UUID.randomUUID();
        UUID               valId    = UUID.randomUUID();
        InvestmentResponse response = buildFciCuotapartesResponse(id);

        given(investmentService.updateValuation(eq(id), eq(valId), any(InvestmentValuationRequest.class), any(User.class)))
                .willReturn(response);

        mockMvc.perform(put("/api/investments/" + id + "/valuations/" + valId)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildValuationRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FCI_CUOTAPARTES"));
    }

    @Test
    @DisplayName("PUT /{id}/valuations/{valId} cuando la valuación no existe retorna 404")
    void updateValuation_notFound_returns404() throws Exception {
        UUID id    = UUID.randomUUID();
        UUID valId = UUID.randomUUID();

        given(investmentService.updateValuation(eq(id), eq(valId), any(InvestmentValuationRequest.class), any(User.class)))
                .willThrow(new InvestmentValuationNotFoundException(valId));

        mockMvc.perform(put("/api/investments/" + id + "/valuations/" + valId)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildValuationRequest())))
                .andExpect(status().isNotFound());
    }

    // ─── DELETE /api/investments/{id}/valuations/{valId} ─────────────────────

    @Test
    @DisplayName("DELETE /{id}/valuations/{valId} con token retorna 200 y el activo actualizado")
    void deleteValuation_validRequest_returns200() throws Exception {
        UUID               id       = UUID.randomUUID();
        UUID               valId    = UUID.randomUUID();
        InvestmentResponse response = buildFciCuotapartesResponse(id);

        given(investmentService.deleteValuation(eq(id), eq(valId), any(User.class)))
                .willReturn(response);

        mockMvc.perform(delete("/api/investments/" + id + "/valuations/" + valId)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FCI_CUOTAPARTES"));
    }

    @Test
    @DisplayName("DELETE /{id}/valuations/{valId} cuando la valuación no existe retorna 404")
    void deleteValuation_notFound_returns404() throws Exception {
        UUID id    = UUID.randomUUID();
        UUID valId = UUID.randomUUID();

        given(investmentService.deleteValuation(eq(id), eq(valId), any(User.class)))
                .willThrow(new InvestmentValuationNotFoundException(valId));

        mockMvc.perform(delete("/api/investments/" + id + "/valuations/" + valId)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isNotFound());
    }

    // ─── GET /api/investments/market/fci-funds ────────────────────────────────

    @Test
    @DisplayName("GET /market/fci-funds con token retorna 200 y la lista de fondos")
    void getFciFunds_returns200_whenAuthenticated() throws Exception {
        FciFundDto fund = new FciFundDto("Cocos Capital - Clase A", "mercadoDinero",
                new BigDecimal("1099.3320"), java.time.LocalDate.of(2026, 6, 24));

        given(fciValuationSyncService.getLatestFciFunds("mercadoDinero"))
                .willReturn(List.of(fund));

        mockMvc.perform(get("/api/investments/market/fci-funds")
                        .param("categoria", "mercadoDinero")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fondo").value("Cocos Capital - Clase A"))
                .andExpect(jsonPath("$[0].categoria").value("mercadoDinero"));
    }

    @Test
    @DisplayName("GET /market/fci-funds sin token retorna 401")
    void getFciFunds_returns401_whenNoToken() throws Exception {
        mockMvc.perform(get("/api/investments/market/fci-funds"))
                .andExpect(status().isUnauthorized());
    }

    // ─── GET /api/investments/market/instruments ──────────────────────────────

    @Test
    @DisplayName("GET /market/instruments con token retorna 200 y la lista de instrumentos")
    void getInstruments_returns200_whenAuthenticated() throws Exception {
        InstrumentDto instrument = new InstrumentDto("AL30", "BONO SOBERANO AL30", "BONO",
                new BigDecimal("1450.5000"), java.time.LocalDate.of(2026, 6, 24),
                java.time.LocalDate.of(2030, 7, 9));

        given(ppiValuationSyncService.getInstrumentsByType("BONO"))
                .willReturn(List.of(instrument));

        mockMvc.perform(get("/api/investments/market/instruments")
                        .param("tipo", "BONO")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").value("AL30"))
                .andExpect(jsonPath("$[0].tipo").value("BONO"));
    }

    @Test
    @DisplayName("GET /market/instruments sin token retorna 401")
    void getInstruments_returns401_whenNoToken() throws Exception {
        mockMvc.perform(get("/api/investments/market/instruments"))
                .andExpect(status().isUnauthorized());
    }

    // ─── GET /api/investments/market/fci-vcp ──────────────────────────────────

    @Test
    @DisplayName("GET /market/fci-vcp con fondo existente retorna 200 y FciFundDto")
    void getFciVcp_returns200_whenSnapshotExists() throws Exception {
        LocalDate fecha = LocalDate.of(2026, 6, 25);
        FciFundDto dto = new FciFundDto("Cocos Capital - Clase A", "mercadoDinero",
                new java.math.BigDecimal("1099.3320"), fecha);

        given(fciValuationSyncService.getVcpForDate("Cocos Capital - Clase A", fecha))
                .willReturn(Optional.of(dto));

        mockMvc.perform(get("/api/investments/market/fci-vcp")
                        .param("fondo", "Cocos Capital - Clase A")
                        .param("fecha", "2026-06-25")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fondo").value("Cocos Capital - Clase A"))
                .andExpect(jsonPath("$.categoria").value("mercadoDinero"));
    }

    @Test
    @DisplayName("GET /market/fci-vcp con fondo inexistente retorna 204 No Content (no 404)")
    void getFciVcp_returns204_whenSnapshotNotFound() throws Exception {
        LocalDate fecha = LocalDate.of(2026, 6, 25);

        given(fciValuationSyncService.getVcpForDate("fondo-inexistente", fecha))
                .willReturn(Optional.empty());

        mockMvc.perform(get("/api/investments/market/fci-vcp")
                        .param("fondo", "fondo-inexistente")
                        .param("fecha", "2026-06-25")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("GET /market/fci-vcp sin token retorna 401")
    void getFciVcp_returns401_whenNoToken() throws Exception {
        mockMvc.perform(get("/api/investments/market/fci-vcp")
                        .param("fondo", "Cocos Capital - Clase A")
                        .param("fecha", "2026-06-25"))
                .andExpect(status().isUnauthorized());
    }

    // ─── GET /api/investments/market/instrument-price ─────────────────────────

    @Test
    @DisplayName("GET /market/instrument-price con PPI configurado y ticker existente retorna 200 con pricePerUnit")
    void getInstrumentPrice_returns200_whenPpiConfiguredAndTickerFound() throws Exception {
        LocalDate fecha = LocalDate.of(2026, 6, 25);

        given(ppiMarketDataClient.isConfigured()).willReturn(true);
        // El cierre real es del 24/06, anterior a la fecha solicitada (25/06): el response lo refleja.
        given(ppiMarketDataClient.getPriceForDate("AL30", "BONOS", fecha))
                .willReturn(Optional.of(new com.vectis.backend.config.PpiMarketDataClient.DatedPrice(
                        LocalDate.of(2026, 6, 24), new java.math.BigDecimal("963.0000"))));

        mockMvc.perform(get("/api/investments/market/instrument-price")
                        .param("ticker", "AL30")
                        .param("type", "BONO")
                        .param("fecha", "2026-06-25")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AL30"))
                .andExpect(jsonPath("$.type").value("BONO"))
                .andExpect(jsonPath("$.fecha").value("2026-06-24")) // fecha del cierre real, no la solicitada
                .andExpect(jsonPath("$.pricePerUnit").value(963.0));
    }

    @Test
    @DisplayName("GET /market/instrument-price con PPI no configurado retorna 503")
    void getInstrumentPrice_returns503_whenPpiNotConfigured() throws Exception {
        given(ppiMarketDataClient.isConfigured()).willReturn(false);

        mockMvc.perform(get("/api/investments/market/instrument-price")
                        .param("ticker", "AL30")
                        .param("type", "BONO")
                        .param("fecha", "2026-06-25")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("PPI no configurado"));
    }

    @Test
    @DisplayName("GET /market/instrument-price cuando el ticker no tiene datos retorna 204 No Content (no 404)")
    void getInstrumentPrice_returns204_whenTickerNotFound() throws Exception {
        LocalDate fecha = LocalDate.of(2026, 6, 25);

        given(ppiMarketDataClient.isConfigured()).willReturn(true);
        given(ppiMarketDataClient.getPriceForDate("AL30", "BONOS", fecha))
                .willReturn(Optional.empty());

        mockMvc.perform(get("/api/investments/market/instrument-price")
                        .param("ticker", "AL30")
                        .param("type", "BONO")
                        .param("fecha", "2026-06-25")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("GET /market/instrument-price sin token retorna 401")
    void getInstrumentPrice_returns401_whenNoToken() throws Exception {
        mockMvc.perform(get("/api/investments/market/instrument-price")
                        .param("ticker", "AL30")
                        .param("type", "BONO")
                        .param("fecha", "2026-06-25"))
                .andExpect(status().isUnauthorized());
    }
}
