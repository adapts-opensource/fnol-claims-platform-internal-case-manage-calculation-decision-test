package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * NFR Compliance Notes:
 * - Input Validation: Fails fast when jurisdiction is not in supported catalog.
 * - Security: Enforces least-privilege routing by rejecting unsupported jurisdictions.
 * - Observability: Uses structured logging for audit trails during decision calculation.
 * - Concurrency: Stateless calculator implementation ensures thread-safe execution.
 */
@ExtendWith(MockitoExtension.class)
public class JurisdictionMustMatchSupportedCatalogTest {

    @Mock
    private ReferenceDataService referenceDataService;

    @InjectMocks
    private ClaimRoutingDecisionCalculator routingCalculator;

    private static final String VALID_JURISDICTION = "CA";
    private static final String INVALID_JURISDICTION = "ZZ";
    private static final Set<String> SUPPORTED_JURISDICTIONS = Set.of("CA", "NY", "TX");

    @BeforeEach
    void setUp() {
        // Mock reference data service (abstracts Redis/DynamoDB cache & reference data lookup)
        when(referenceDataService.getSupportedJurisdictions()).thenReturn(SUPPORTED_JURISDICTIONS);
    }

    @Test
    void jurisdiction_must_match_supported_catalog() {
        // Arrange: Valid payload with supported jurisdiction
        Map<String, Object> validPayload = Map.of(
            "id", "claim-init-001",
            "jurisdiction", VALID_JURISDICTION
        );

        // Act & Assert: Should process without throwing validation exception
        assertDoesNotThrow(() -> routingCalculator.calculateRoutingDecision(validPayload));
        verify(referenceDataService, times(1)).getSupportedJurisdictions();
    }

    @Test
    void jurisdiction_must_match_supported_catalog_invalid_jurisdiction_throws_validation_exception() {
        // Arrange: Payload with unsupported jurisdiction
        Map<String, Object> invalidPayload = Map.of(
            "id", "claim-init-002",
            "jurisdiction", INVALID_JURISDICTION
        );

        // Act & Assert: Should fail fast with input validation error
        RoutingValidationException exception = assertThrows(RoutingValidationException.class,
                () -> routingCalculator.calculateRoutingDecision(invalidPayload));
        assertEquals("Jurisdiction not found in supported catalog: " + INVALID_JURISDICTION, exception.getMessage());
    }
}

// Supporting interfaces/classes for compilation context
interface ReferenceDataService {
    Set<String> getSupportedJurisdictions();
}

class RoutingValidationException extends RuntimeException {
    public RoutingValidationException(String message) {
        super(message);
    }
}

class ClaimRoutingDecisionCalculator {
    private final ReferenceDataService referenceDataService;
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger("ClaimRouting");

    public ClaimRoutingDecisionCalculator(ReferenceDataService referenceDataService) {
        this.referenceDataService = referenceDataService;
    }

    public void calculateRoutingDecision(Map<String, Object> payload) {
        String jurisdiction = (String) payload.get("jurisdiction");
        logger.info(java.util.Map.of("event", "validate_jurisdiction", "jurisdiction", jurisdiction).toString());
        if (!referenceDataService.getSupportedJurisdictions().contains(jurisdiction)) {
            throw new RoutingValidationException("Jurisdiction not found in supported catalog: " + jurisdiction);
        }
    }
}
