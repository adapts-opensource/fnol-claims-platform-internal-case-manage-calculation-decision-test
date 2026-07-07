package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductFormMustExistInActiveCatalogTest {

    @Mock
    private CatalogService catalogService;

    private ClaimDataStandardizationValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ClaimDataStandardizationValidator(catalogService);
    }

    @Test
    void productFormMustExistInActiveCatalog_validForm_shouldPassValidation() {
        Map<String, Object> payload = Map.of("productFormId", "PF_123");
        when(catalogService.isFormActive("PF_123")).thenReturn(true);

        assertDoesNotThrow(() -> validator.validate(payload));
    }

    @Test
    void productFormMustExistInActiveCatalog_missingForm_shouldFailValidation() {
        Map<String, Object> payload = Map.of("productFormId", "PF_999");
        when(catalogService.isFormActive("PF_999")).thenReturn(false);

        assertThrows(ValidationException.class, () -> validator.validate(payload));
    }

    @Test
    void productFormMustExistInActiveCatalog_inactiveForm_shouldFailValidation() {
        Map<String, Object> payload = Map.of("productFormId", "PF_456");
        when(catalogService.isFormActive("PF_456")).thenReturn(false);

        assertThrows(ValidationException.class, () -> validator.validate(payload));
    }

    // Supporting classes for self-contained compilation and mock isolation
    static class CatalogService {
        boolean isFormActive(String formId) {
            throw new UnsupportedOperationException("Should be mocked");
        }
    }

    static class ClaimDataStandardizationValidator {
        private final CatalogService catalogService;

        ClaimDataStandardizationValidator(CatalogService catalogService) {
            this.catalogService = catalogService;
        }

        void validate(Map<String, Object> payload) {
            Object formIdObj = payload.get("productFormId");
            if (formIdObj == null) {
                throw new ValidationException("Input validation failed: productFormId is required");
            }
            String formId = formIdObj.toString();
            if (!catalogService.isFormActive(formId)) {
                throw new ValidationException("Product form must exist in active catalog: " + formId);
            }
        }
    }

    static class ValidationException extends RuntimeException {
        ValidationException(String message) {
            super(message);
        }
    }
}
