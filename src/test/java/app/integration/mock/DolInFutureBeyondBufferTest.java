package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationValidationDecisionMockTest {

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private RulesEngineService rulesEngineService;

    @InjectMocks
    private ClaimDataStandardizationValidationService validationService;

    @BeforeEach
    void setUp() {
        // Mocks are initialized by MockitoExtension
    }

    @Test
    @DisplayName("Dol In Future Beyond Buffer")
    void dolInFutureBeyondBuffer() {
        // Arrange
        String claimId = "CLM-TEST-DOL-FUTURE";
        ZonedDateTime now = ZonedDateTime.now();
        // Simulate DOL far in future, exceeding typical buffer (e.g., 5 years vs 30 days)
        ZonedDateTime futureDol = now.plusYears(5);

        Map<String, Object> payload = Map.of(
            "dateOfLoss", futureDol.toString(),
            "policyNumber", "POL-999"
        );
        Map<String, Object> input = Map.of(
            "id", claimId,
            "payload", payload
        );

        // Mock S3 read for claim document
        when(documentStoreService.read(eq("DocumentStoreService-bucket"), eq("DocumentStoreService/" + claimId + ".json")))
            .thenReturn("s3://DocumentStoreService-bucket/DocumentStoreService/" + claimId + ".json");

        // Mock Rules Engine to provide buffer configuration
        when(rulesEngineService.getItem(eq("RulesEngineService_table"), eq("pk")))
            .thenReturn(Map.of("bufferDays", 30));

        // Act
        ValidationDecision decision = validationService.validate(input);

        // Assert
        assertNotNull(decision, "Decision should not be null");
        assertEquals(ValidationStatus.INVALID, decision.getStatus(), "Status should be INVALID");
        assertTrue(decision.getErrors().stream()
            .anyMatch(e -> "DOL_FUTURE_BUFFER_EXCEEDED".equals(e.getCode())),
            "Should contain DOL_FUTURE_BUFFER_EXCEEDED error");
    }
}
