package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Minimal interfaces for mocked external I/O to ensure standalone compilation
interface RulesEngineService {
    Map<String, Object> resolveRules(String productForm);
}

interface DocumentStoreService {
    String store(String entityId, String payloadJson);
}

class ClaimDataStandardizationProcessor {
    private final RulesEngineService rulesEngineService;
    private final DocumentStoreService documentStoreService;

    ClaimDataStandardizationProcessor(RulesEngineService rulesEngineService, DocumentStoreService documentStoreService) {
        this.rulesEngineService = rulesEngineService;
        this.documentStoreService = documentStoreService;
    }

    Map<String, Object> process(String entityId, Map<String, Object> payload) {
        String productForm = (String) payload.get("productForm");
        Map<String, Object> coverageRules = rulesEngineService.resolveRules(productForm);
        String storedUri = documentStoreService.store(entityId, payload.toString());

        return Map.of(
            "id", entityId,
            "payload", payload,
            "appliedCoverageRules", coverageRules,
            "validationStatus", "VALIDATED",
            "storedObjectUri", storedUri
        );
    }
}

@ExtendWith(MockitoExtension.class)
public class ProductFormValidationAppliesCorrectCoverageRulesTest {

    @Mock
    private RulesEngineService rulesEngineService;

    @Mock
    private DocumentStoreService documentStoreService;

    private ClaimDataStandardizationProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ClaimDataStandardizationProcessor(rulesEngineService, documentStoreService);
    }

    @Test
    void product_form_validation_applies_correct_coverage_rules() {
        // Arrange
        String entityId = "claim-std-001";
        Map<String, Object> payload = Map.of(
            "productForm", "AUTO_FULL",
            "coverageTier", "PREMIUM",
            "jurisdiction", "CA"
        );

        Map<String, Object> expectedCoverageRules = Map.of(
            "collision", true,
            "comprehensive", true,
            "uninsuredMotorist", true,
            "limitPerOccurrence", 1000000
        );

        when(rulesEngineService.resolveRules(anyString())).thenReturn(expectedCoverageRules);
        when(documentStoreService.store(anyString(), anyString())).thenReturn("s3://newco-docs/claim-std-001.json");

        // Act
        Map<String, Object> result = processor.process(entityId, payload);

        // Assert
        assertNotNull(result, "Result payload should not be null");
        assertTrue(result.containsKey("appliedCoverageRules"), "Result should contain applied coverage rules");
        assertEquals(expectedCoverageRules, result.get("appliedCoverageRules"), "Coverage rules must match expected product form validation");
        assertEquals("VALIDATED", result.get("validationStatus"), "Validation status should be VALIDATED");
        assertEquals(entityId, result.get("id"), "Entity ID must be preserved");

        // Verify external I/O interactions (mocked, no live calls)
        verify(rulesEngineService, times(1)).resolveRules("AUTO_FULL");
        verify(documentStoreService, times(1)).store(eq(entityId), anyString());
    }
}
