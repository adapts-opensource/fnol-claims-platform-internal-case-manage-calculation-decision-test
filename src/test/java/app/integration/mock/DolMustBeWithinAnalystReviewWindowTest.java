package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Mock test for Claim Data Standardization:validation:decision feature.
 * Verifies validation logic against mocked infrastructure contracts.
 * 
 * NFR Compliance:
 * - Security: No hardcoded secrets; input validation via payload structure.
 * - Observability: Structured logging via Logger.
 * - Compliance: No PII in test data; GDPR/SOC2 safe mocks.
 * - Concurrency: Stateless mocks ensure thread safety in isolation.
 */
@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationValidationDecisionMockTest {

    private static final Logger LOGGER = Logger.getLogger(ClaimDataStandardizationValidationDecisionMockTest.class.getName());

    @Mock
    private RulesEngineService rulesEngineService;

    @Mock
    private DocumentStoreService documentStoreService;

    @InjectMocks
    private ClaimDataStandardizationValidationDecisionService service;

    @BeforeEach
    void setUp() {
        LOGGER.info("Initializing mock context for Claim Data Standardization Validation Decision");
    }

    @Test
    void dolMustBeWithinAnalystReviewWindow() {
        // Arrange
        String claimId = "CLM-MOCK-DOL-WINDOW-" + ChronoUnit.MILLIS.between(LocalDate.now().atStartOfDay(), LocalDate.now().atStartOfDay());
        LocalDate currentDate = LocalDate.now();
        LocalDate dolDate = currentDate.minusDays(5); // Within typical 10-day window
        LocalDate windowStart = currentDate.minusDays(10);

        Map<String, Object> payload = Map.of(
            "id", claimId,
            "dateOfLoss", dolDate.toString()
        );

        when(rulesEngineService.getAnalystReviewWindow(claimId))
            .thenReturn(Optional.of(new ReviewWindow(windowStart, currentDate)));

        // Act
        ValidationResult result = service.validate(payload);

        // Assert
        assertTrue(result.isValid(), "DOL must be within analyst review window");
        
        // Verify infrastructure interactions
        verify(rulesEngineService).getAnalystReviewWindow(claimId);
        verifyNoMoreInteractions(rulesEngineService);
        
        LOGGER.log(Level.INFO, "Validation passed: DOL within review window for claim {0}", claimId);
    }
}
