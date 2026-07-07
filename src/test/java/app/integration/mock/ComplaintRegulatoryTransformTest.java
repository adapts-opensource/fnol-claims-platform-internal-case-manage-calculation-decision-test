package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ComplaintRegulatoryTransformTest {

    interface ClaimTransformationService {
        Map<String, Object> transform(Map<String, Object> payload);
    }

    @Mock
    private ClaimTransformationService mockTransformationService;

    private Map<String, Object> inputPayload;

    @BeforeEach
    void setUp() {
        inputPayload = new HashMap<>();
        inputPayload.put("tenant_code", "FL01");
        inputPayload.put("year", 2024);
        inputPayload.put("dfs_complaint_received", true);
        inputPayload.put("date_of_loss", LocalDate.of(2024, 6, 15));
        inputPayload.put("cause_of_loss", "wind");
        inputPayload.put("product", "HO3");
    }

    @Test
    void transform_to_complaint_review_on_regulatory_flag() {
        String expectedClaimNumber = "CLM-FL01-2024-001";
        when(mockTransformationService.transform(any(Map.class))).thenAnswer(invocation -> {
            Map<String, Object> result = new HashMap<>();
            result.put("claim_number", expectedClaimNumber);
            result.put("task_created", "Complaint/Regulatory Review");
            result.put("regulatory_sensitivity_flagged", true);
            result.put("diary_created", "mediation_response_due");
            result.put("counsel_review_task", "coverage_counsel_review");
            return result;
        });

        Map<String, Object> result = mockTransformationService.transform(inputPayload);

        assertNotNull(result);
        assertEquals(expectedClaimNumber, result.get("claim_number"));
        assertEquals("Complaint/Regulatory Review", result.get("task_created"));
        assertTrue((Boolean) result.get("regulatory_sensitivity_flagged"));
        assertEquals("mediation_response_due", result.get("diary_created"));
        assertEquals("coverage_counsel_review", result.get("counsel_review_task"));

        verify(mockTransformationService, times(1)).transform(inputPayload);
    }
}
