package app.integration.e2e;

import app.models.CalculationDecisionRequest;
import app.models.CalculationDecisionResponse;
import app.services.InternalCaseManagementCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Internal Case Management: Calculation Decision E2E Tests")
public class InternalCaseManagementCalculationDecisionE2eTest {

    private InternalCaseManagementCalculationService calculationService;

    // Constants sidecar fixture data used to build test inputs
    private static final Map<String, Object> TEST_PAYLOAD = Map.of(
        "caseId", "C-2024-001",
        "insuredId", "INS-7890",
        "claimType", "AUTO",
        "lossDate", "2024-05-20",
        "estimatedRepairCost", 2500.00,
        "deductible", 500.00
    );

    @BeforeEach
    void setUp() {
        // E2E: Instantiate real service without mocks or test doubles
        this.calculationService = new InternalCaseManagementCalculationService();
    }

    @Test
    @DisplayName("construct_request_payloads_for_internal_case_management_calculation_decision_without_mocks")
    void constructRequestPayloadsForInternalCaseManagementCalculationDecisionWithoutMocks() {
        // Build request payload from test-case Inputs / Expected Results and Constants JSON sidecar
        String caseId = (String) TEST_PAYLOAD.get("caseId");
        String insuredId = (String) TEST_PAYLOAD.get("insuredId");
        String claimType = (String) TEST_PAYLOAD.get("claimType");
        String lossDate = (String) TEST_PAYLOAD.get("lossDate");
        double estimatedRepairCost = (double) TEST_PAYLOAD.get("estimatedRepairCost");
        double deductible = (double) TEST_PAYLOAD.get("deductible");

        CalculationDecisionRequest request = new CalculationDecisionRequest();
        request.setCaseId(caseId);
        request.setInsuredId(insuredId);
        request.setClaimType(claimType);
        request.setLossDate(lossDate);
        request.setEstimatedRepairCost(estimatedRepairCost);
        request.setDeductible(deductible);
        request.setPayload(TEST_PAYLOAD);

        // Invoke real service end-to-end
        CalculationDecisionResponse response = calculationService.decide(request);

        // Assertions on handler outcomes
        assertNotNull(response, "Response must not be null");
        assertEquals(caseId, response.getCaseId(), "Case ID must match input");
        assertEquals("APPROVED", response.getDecisionStatus(), "Decision status must match expected result");
        assertEquals(2000.00, response.getApprovedAmount(), 0.01, "Approved amount must be estimated cost minus deductible");
    }
}
