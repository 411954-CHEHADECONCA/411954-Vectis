package com.vectis.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vectis.backend.config.SecurityConfig;
import com.vectis.backend.domain.entity.CategoryType;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.CategoryRequest;
import com.vectis.backend.dto.CategoryResponse;
import com.vectis.backend.exception.CategoryNotFoundException;
import com.vectis.backend.exception.VectisException;
import com.vectis.backend.repository.UserRepository;
import com.vectis.backend.service.CategoryService;
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

@WebMvcTest(CategoryController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("CategoryController")
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean private CategoryService categoryService;
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

    // ─── GET /api/categories ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/categories sin token retorna 401")
    void getCategories_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/categories con token retorna 200 y la lista")
    void getCategories_withToken_returns200WithList() throws Exception {
        CategoryResponse response = new CategoryResponse(
                UUID.randomUUID(), "Alimentos", "utensils", "#10B981", CategoryType.EXPENSE, true);
        given(categoryService.getCategories(userId)).willReturn(List.of(response));

        mockMvc.perform(get("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Alimentos"))
                .andExpect(jsonPath("$[0].isDefault").value(true));
    }

    // ─── POST /api/categories ─────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/categories con body válido retorna 201")
    void createCategory_validRequest_returns201() throws Exception {
        CategoryRequest request = new CategoryRequest("Gym", "dumbbell", "#EC4899", CategoryType.EXPENSE);
        CategoryResponse response = new CategoryResponse(
                UUID.randomUUID(), "Gym", "dumbbell", "#EC4899", CategoryType.EXPENSE, false);

        given(categoryService.createCategory(any(CategoryRequest.class), any(User.class))).willReturn(response);

        mockMvc.perform(post("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Gym"))
                .andExpect(jsonPath("$.isDefault").value(false));
    }

    @Test
    @DisplayName("POST /api/categories con nombre en blanco retorna 400")
    void createCategory_blankName_returns400() throws Exception {
        CategoryRequest request = new CategoryRequest("", "dumbbell", "#EC4899", CategoryType.EXPENSE);

        mockMvc.perform(post("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/categories con nombre duplicado retorna 409")
    void createCategory_duplicateName_returns409() throws Exception {
        CategoryRequest request = new CategoryRequest("Alimentos", "utensils", "#10B981", CategoryType.EXPENSE);

        given(categoryService.createCategory(any(CategoryRequest.class), any(User.class)))
                .willThrow(new VectisException("Ya existe", HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
    }

    // ─── DELETE /api/categories/{id} ─────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/categories/{id} sin token retorna 401")
    void deleteCategory_withoutToken_returns401() throws Exception {
        mockMvc.perform(delete("/api/categories/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /api/categories/{id} de categoría global retorna 403")
    void deleteCategory_globalCategory_returns403() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new VectisException("No permitido", HttpStatus.FORBIDDEN))
                .when(categoryService).deleteCategory(eq(id), any(User.class));

        mockMvc.perform(delete("/api/categories/" + id)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("DELETE /api/categories/{id} de categoría inexistente retorna 404")
    void deleteCategory_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new CategoryNotFoundException(id))
                .when(categoryService).deleteCategory(eq(id), any(User.class));

        mockMvc.perform(delete("/api/categories/" + id)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/categories/{id} propia retorna 204")
    void deleteCategory_ownCategory_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(categoryService).deleteCategory(eq(id), any(User.class));

        mockMvc.perform(delete("/api/categories/" + id)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isNoContent());
    }
}
