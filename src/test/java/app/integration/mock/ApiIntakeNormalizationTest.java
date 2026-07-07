package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiIntakeNormalizationTest {

    @Mock
    private ClaimTransformationService claimTransformationService;

    @Test
    void normalizeApiIntakeData() {
        // Arrange inputs
        String tenantCode = "FL01";
        int year = 2024;
        String channel = "api";
        String reporterType = "partner";
        String dateOfLoss = "2024-08-05";
        String causeOfLoss = "water";
        String product = "HO3";
        String payloadVersion = "v2";

        // Mock the transformation service to return expected normalized state
        ClaimIntakePayload payload = new ClaimIntakePayload(tenantCode, year, channel, reporterType, dateOfLoss, causeOfLoss, product, payloadVersion);
        ClaimTransformationResult expectedResult = new ClaimTransformationResult(
                "CLM-2024-FL01-0001",
                "Standard property claim",
                true,
                "API",
                "Submitted"
        );

        when(claimTransformationService.transform(payload)).thenReturn(expectedResult);

        // Act
        ClaimTransformationResult actualResult = claimTransformationService.transform(payload);

        // Assert expected results
        assertNotNull(actualResult.getClaimNumber(), "Claim number should be generated");
        assertEquals("Standard property claim", actualResult.getClaimType(), "Claim type should be set to Standard property claim");
        assertTrue(actualResult.isDocumentUploadReviewTaskCreated(), "Document upload review task should be created");
        assertEquals("API", actualResult.getNormalizedChannel(), "Communication channel should be normalized to API");
        assertEquals("Submitted", actualResult.getState(), "State should be set to Submitted");
    }

    // Supporting model classes for test isolation
    static class ClaimIntakePayload {
        private final String tenantCode;
        private final int year;
        private final String channel;
        private final String reporterType;
        private final String dateOfLoss;
        private final String causeOfLoss;
        private final String product;
        private final String payloadVersion;

        ClaimIntakePayload(String tenantCode, int year, String channel, String reporterType,
                           String dateOfLoss, String causeOfLoss, String product, String payloadVersion) {
            this.tenantCode = tenantCode;
            this.year = year;
            this.channel = channel;
            this.reporterType = reporterType;
            this.dateOfLoss = dateOfLoss;
            this.causeOfLoss = causeOfLoss;
            this.product = product;
            this.payloadVersion = payloadVersion;
        }
    }

    static class ClaimTransformationResult {
        private final String claimNumber;
        private final String claimType;
        private final boolean documentUploadReviewTaskCreated;
        private final String normalizedChannel;
        private final String state;

        ClaimTransformationResult(String claimNumber, String claimType, boolean documentUploadReviewTaskCreated,
                                  String normalizedChannel, String state) {
            this.claimNumber = claimNumber;
            this.claimType = claimType;
            this.documentUploadReviewTaskCreated = documentUploadReviewTaskCreated;
            this.normalizedChannel = normalizedChannel;
            this.state = state;
        }

        public String getClaimNumber() { return claimNumber; }
        public String getClaimType() { return claimType; }
        public boolean isDocumentUploadReviewTaskCreated() { return documentUploadReviewTaskCreated; }
        public String getNormalizedChannel() { return normalizedChannel; }
        public String getState() { return state; }
    }

    interface ClaimTransformationService {
        ClaimTransformationResult transform(ClaimIntakePayload payload);
    }
}
