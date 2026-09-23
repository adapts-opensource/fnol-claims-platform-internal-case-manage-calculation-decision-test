package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationDecisionMockTest {

    @Mock
    private ClaimDataTransformationService transformationService;

    private ClaimDataStandardizationProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ClaimDataStandardizationProcessor(transformationService);
    }

    @Test
    void policy_dates_missing_flag_for_manual_review() {
        // Arrange
        String claimId = "CLM-STD-001";
        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("policyStartDate", null);
        inputPayload.put("policyEndDate", null);

        Map<String, Object> expectedOutput = new HashMap<>();
        expectedOutput.put("id", claimId);
        expectedOutput.put("manualReviewRequired", true);
        expectedOutput.put("reviewReason", "Policy dates missing");

        when(transformationService.transform(inputPayload)).thenReturn(expectedOutput);

        // Act
        Map<String, Object> result = processor.process(claimId, inputPayload);

        // Assert
        assertNotNull(result, "Result payload must not be null");
        assertEquals(claimId, result.get("id"), "Claim ID should be preserved");
        assertTrue((Boolean) result.get("manualReviewRequired"), "Manual review flag should be set when policy dates are missing");
        assertEquals("Policy dates missing", result.get("reviewReason"), "Review reason should indicate missing policy dates");
        verify(transformationService).transform(inputPayload);
    }
}

interface ClaimDataTransformationService {
    Map<String, Object> transform(Map<String, Object> payload);
}

class ClaimDataStandardizationProcessor {
    private final ClaimDataTransformationService transformationService;

    ClaimDataStandardizationProcessor(ClaimDataTransformationService transformationService) {
        this.transformationService = transformationService;
    }

    Map<String, Object> process(String id, Map<String, Object> payload) {
        Map<String, Object> transformed = transformationService.transform(payload);
        transformed.put("id", id);
        return transformed;
    }
}
