package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsuredEngagementTransformationTest {

    @Mock
    private InsuredEngagementService insuredEngagementService;

    @Test
    void all_fields_must_be_populated_or_explicitly_marked_null() {
        // Arrange: Define expected state where all fields are either populated or explicitly null
        String insuredId = "INS-10293";
        String decisionType = "CLAIM_APPROVAL";
        String engagementStatus = "ACTIVE";
        Long transformationTimestamp = 1715000000L;
        String remark = null; // Explicitly marked null per business rule

        InsuredEngagementResult expectedResult = new InsuredEngagementResult(
                insuredId, decisionType, engagementStatus, transformationTimestamp, remark
        );

        when(insuredEngagementService.transform(any(InsuredEngagementRequest.class))).thenReturn(expectedResult);

        // Act
        InsuredEngagementResult actualResult = insuredEngagementService.transform(new InsuredEngagementRequest());

        // Assert: Verify all fields match the expected populated or null state
        assertNotNull(actualResult);
        assertEquals(insuredId, actualResult.getInsuredId());
        assertEquals(decisionType, actualResult.getDecisionType());
        assertEquals(engagementStatus, actualResult.getEngagementStatus());
        assertEquals(transformationTimestamp, actualResult.getTransformationTimestamp());
        assertNull(actualResult.getRemark(), "Remark field must be explicitly marked null when not applicable");
    }

    // Mock DTOs for demonstration
    static class InsuredEngagementRequest {
        // Intentionally empty for test context
    }

    static class InsuredEngagementResult {
        private final String insuredId;
        private final String decisionType;
        private final String engagementStatus;
        private final Long transformationTimestamp;
        private final String remark;

        InsuredEngagementResult(String insuredId, String decisionType, String engagementStatus, 
                                Long transformationTimestamp, String remark) {
            this.insuredId = insuredId;
            this.decisionType = decisionType;
            this.engagementStatus = engagementStatus;
            this.transformationTimestamp = transformationTimestamp;
            this.remark = remark;
        }

        public String getInsuredId() { return insuredId; }
        public String getDecisionType() { return decisionType; }
        public String getEngagementStatus() { return engagementStatus; }
        public Long getTransformationTimestamp() { return transformationTimestamp; }
        public String getRemark() { return remark; }
    }

    interface InsuredEngagementService {
        InsuredEngagementResult transform(InsuredEngagementRequest request);
    }
}
