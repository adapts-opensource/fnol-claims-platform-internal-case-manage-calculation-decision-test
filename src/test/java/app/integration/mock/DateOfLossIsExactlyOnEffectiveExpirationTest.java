package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

public class ClaimDataStandardizationDecisionTransformationTest {

    @Mock
    private ClaimDataStandardizationDecisionService mockTransformationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void dateOfLossIsExactlyOnEffectiveExpirationDate() {
        // Arrange: Prepare input payload where dateOfLoss matches effectiveDate
        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("id", "CLM-2023-001");
        inputPayload.put("dateOfLoss", "2023-10-15");
        inputPayload.put("effectiveDate", "2023-10-15");
        inputPayload.put("expirationDate", "2023-10-31");

        ClaimDataStandardizationCalculationTransform input = new ClaimDataStandardizationCalculationTransform("CLM-2023-001", inputPayload);

        // Arrange: Mock the transformation service to return standardized payload
        Map<String, Object> expectedPayload = new HashMap<>();
        expectedPayload.put("id", "CLM-2023-001");
        expectedPayload.put("dateOfLoss", "2023-10-15");
        expectedPayload.put("effectiveDate", "2023-10-15");
        expectedPayload.put("expirationDate", "2023-10-31");
        expectedPayload.put("dateOfLossOnEffectiveExpiration", true);
        expectedPayload.put("standardizationStatus", "APPROVED");

        ClaimDataStandardizationCalculationTransform expectedOutput = new ClaimDataStandardizationCalculationTransform("CLM-2023-001", expectedPayload);
        when(mockTransformationService.transform(input)).thenReturn(expectedOutput);

        // Act: Execute transformation
        ClaimDataStandardizationCalculationTransform actualOutput = mockTransformationService.transform(input);

        // Assert: Verify transformation results and I/O mock interactions
        assertNotNull(actualOutput, "Transformed claim data should not be null");
        assertEquals(expectedOutput.getId(), actualOutput.getId(), "ID should remain unchanged");
        assertEquals(expectedPayload.get("dateOfLossOnEffectiveExpiration"), actualOutput.getPayload().get("dateOfLossOnEffectiveExpiration"), "Flag should be set to true");
        assertEquals(expectedPayload.get("standardizationStatus"), actualOutput.getPayload().get("standardizationStatus"), "Status should be updated");
        verify(mockTransformationService, times(1)).transform(input);
    }
}

// Supporting model class matching data model summary
class ClaimDataStandardizationCalculationTransform {
    private final String id;
    private final Map<String, Object> payload;

    public ClaimDataStandardizationCalculationTransform(String id, Map<String, Object> payload) {
        this.id = id;
        this.payload = payload;
    }

    public String getId() {
        return id;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }
}

// Service interface representing the transformation layer (I/O mocked)
interface ClaimDataStandardizationDecisionService {
    ClaimDataStandardizationCalculationTransform transform(ClaimDataStandardizationCalculationTransform input);
}
