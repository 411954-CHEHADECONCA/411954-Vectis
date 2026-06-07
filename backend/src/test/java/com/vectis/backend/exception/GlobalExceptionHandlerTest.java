package com.vectis.backend.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    // ─── Controlador de prueba que lanza excepciones adrede ──────────────────

    @RestController
    static class TestController {

        @GetMapping("/test/email-conflict")
        void throwEmailConflict() {
            throw new EmailAlreadyExistsException("dup@test.com");
        }

        @GetMapping("/test/invalid-credentials")
        void throwInvalidCredentials() {
            throw new InvalidCredentialsException();
        }

        @GetMapping("/test/invalid-token")
        void throwInvalidToken() {
            throw new InvalidTokenException("Token not found");
        }

        @GetMapping("/test/generic-error")
        void throwGenericError() {
            throw new RuntimeException("Something went wrong internally");
        }

        record ValidatedBody(@NotBlank(message = "Field is required") String field) {}

        @PostMapping("/test/validation")
        void triggerValidation(@Valid @RequestBody ValidatedBody body) {}
    }

    // ─── VectisException → mapeo de status y mensaje ─────────────────────────

    @Test
    @DisplayName("EmailAlreadyExistsException → 409 Conflict con mensaje de error")
    void emailAlreadyExists_returns409WithMessage() throws Exception {
        mockMvc.perform(get("/test/email-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Email already registered: dup@test.com"));
    }

    @Test
    @DisplayName("InvalidCredentialsException → 401 Unauthorized con mensaje de error")
    void invalidCredentials_returns401WithMessage() throws Exception {
        mockMvc.perform(get("/test/invalid-credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("InvalidTokenException → 401 Unauthorized con mensaje personalizado")
    void invalidToken_returns401WithMessage() throws Exception {
        mockMvc.perform(get("/test/invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Token not found"));
    }

    // ─── MethodArgumentNotValidException → 400 con detalles del campo ────────

    @Test
    @DisplayName("@Valid con campo en blanco → 400 con nombre del campo en el mensaje")
    void validation_blankField_returns400WithFieldName() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"field\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("field")));
    }

    @Test
    @DisplayName("@Valid con campo nulo → 400 con nombre del campo en el mensaje")
    void validation_nullField_returns400WithFieldName() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("field")));
    }

    // ─── Exception genérica → 500 con mensaje opaco ──────────────────────────

    @Test
    @DisplayName("RuntimeException no controlada → 500 con mensaje genérico")
    void unhandledException_returns500WithGenericMessage() throws Exception {
        mockMvc.perform(get("/test/generic-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }
}
