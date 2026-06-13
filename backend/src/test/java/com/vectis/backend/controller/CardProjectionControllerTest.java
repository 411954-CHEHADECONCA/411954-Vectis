package com.vectis.backend.controller;

import com.vectis.backend.config.SecurityConfig;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.CardMatrixResponse;
import com.vectis.backend.dto.CardOverviewResponse;
import com.vectis.backend.repository.UserRepository;
import com.vectis.backend.service.CardProjectionService;
import com.vectis.backend.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CardProjectionController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("CardProjectionController")
class CardProjectionControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private CardProjectionService cardProjectionService;
    @MockBean private JwtService jwtService;
    @MockBean private UserRepository userRepository;

    private UUID userId;
    private static final String VALID_TOKEN = "valid-test-token";
    private static final String AUTH_HEADER  = "Bearer " + VALID_TOKEN;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        User mockUser = User.builder()
                .id(userId).email("user@vectis.com").fullName("Test User").passwordHash("hash").build();
        given(jwtService.isTokenValid(VALID_TOKEN)).willReturn(true);
        given(jwtService.extractUserId(VALID_TOKEN)).willReturn(userId.toString());
        given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));
    }

    @Test
    @DisplayName("GET /api/cards/overview sin token retorna 401")
    void overview_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/cards/overview"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/cards/overview con token retorna 200")
    void overview_withToken_returns200() throws Exception {
        given(cardProjectionService.overview(userId)).willReturn(CardOverviewResponse.builder()
                .cards(List.of()).totalDue(BigDecimal.ZERO)
                .cuotasActivas(List.of()).vencimientos(List.of()).build());

        mockMvc.perform(get("/api/cards/overview").header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDue").value(0));
    }

    @Test
    @DisplayName("GET /api/cards/matrix sin token retorna 401")
    void matrix_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/cards/matrix"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/cards/matrix respeta el parámetro months")
    void matrix_withMonths_returns200() throws Exception {
        given(cardProjectionService.matrix(eq(userId), anyInt())).willReturn(CardMatrixResponse.builder()
                .months(List.of("2026-06", "2026-07", "2026-08"))
                .cards(List.of()).totalsByMonth(List.of()).build());

        mockMvc.perform(get("/api/cards/matrix?months=3").header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months.length()").value(3));
    }
}
