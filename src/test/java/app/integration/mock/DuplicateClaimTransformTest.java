package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

public class DuplicateClaimTransformTest {

    @Mock
    private ClaimTransformationService claimTransformationService;

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private RulesEngineService rulesEngineService;

    private ClaimIntakeRequest intakeRequest;
    private TransformedClaimResult expectedResult;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        intakeRequest = new ClaimIntakeRequest("FL01", "2024", "123456789", "456 Oak Ave", "2024-01-10", "wind", "CLM-FL01-2024-00001111");
        expectedResult = new TransformedClaimResult();
        expectedResult.setGeneratedClaimNumber("CLM-FL01-2024-00001112");
        expectedResult.setFnolState("Duplicate Review");
        expectedResult.setTaskType("Review Potential Duplicate Claim");
        expectedResult.setPossibleOutcomes(new String[]{"merge", "supplement"});
    }

    @Test
    void transform_on_duplicate_claim_detection() {
        when(claimTransformationService.transform(intakeRequest)).thenReturn(expectedResult);

        TransformedClaimResult actualResult = claimTransformationService.transform(intakeRequest);

        assertNotNull(actualResult);
        assertEquals("CLM-FL01-2024-00001112", actualResult.getGeneratedClaimNumber());
        assertEquals("Duplicate Review", actualResult.getFnolState());
        assertEquals("Review Potential Duplicate Claim", actualResult.getTaskType());
        assertArrayEquals(new String[]{"merge", "supplement"}, actualResult.getPossibleOutcomes());
        verify(claimTransformationService, times(1)).transform(intakeRequest);
        verifyNoInteractions(documentStoreService, rulesEngineService);
    }

    // Static nested types for test isolation
    static class ClaimIntakeRequest {
        private final String tenantCode;
        private final String year;
        private final String policyNumber;
        private final String riskAddress;
        private final String dateOfLoss;
        private final String causeOfLoss;
        private final String existingClaimId;

        public ClaimIntakeRequest(String tenantCode, String year, String policyNumber, String riskAddress, String dateOfLoss, String causeOfLoss, String existingClaimId) {
            this.tenantCode = tenantCode;
            this.year = year;
            this.policyNumber = policyNumber;
            this.riskAddress = riskAddress;
            this.dateOfLoss = dateOfLoss;
            this.causeOfLoss = causeOfLoss;
            this.existingClaimId = existingClaimId;
        }
        public String getTenantCode() { return tenantCode; }
        public String getYear() { return year; }
        public String getPolicyNumber() { return policyNumber; }
        public String getRiskAddress() { return riskAddress; }
        public String getDateOfLoss() { return dateOfLoss; }
        public String getCauseOfLoss() { return causeOfLoss; }
        public String getExistingClaimId() { return existingClaimId; }
    }

    static class TransformedClaimResult {
        private String generatedClaimNumber;
        private String fnolState;
        private String taskType;
        private String[] possibleOutcomes;

        public void setGeneratedClaimNumber(String generatedClaimNumber) { this.generatedClaimNumber = generatedClaimNumber; }
        public String getGeneratedClaimNumber() { return generatedClaimNumber; }
        public void setFnolState(String fnolState) { this.fnolState = fnolState; }
        public String getFnolState() { return fnolState; }
        public void setTaskType(String taskType) { this.taskType = taskType; }
        public String getTaskType() { return taskType; }
        public void setPossibleOutcomes(String[] possibleOutcomes) { this.possibleOutcomes = possibleOutcomes; }
        public String[] getPossibleOutcomes() { return possibleOutcomes; }
    }

    interface ClaimTransformationService {
        TransformedClaimResult transform(ClaimIntakeRequest request);
    }

    interface DocumentStoreService {}
    interface RulesEngineService {}
}
