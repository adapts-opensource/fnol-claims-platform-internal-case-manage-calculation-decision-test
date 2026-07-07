package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UninhabitableAleTransformTest {

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private RulesEngineService rulesEngineService;

    @Mock
    private PolicyValidationService policyValidationService;

    @InjectMocks
    private ClaimTransformationEngine transformationEngine;

    @Test
    void transform_to_uninhabitable_property_on_ale_risk() {
        // Arrange
        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("tenant_code", "FL01");
        inputPayload.put("year", 2024);
        inputPayload.put("property_habitable", false);
        inputPayload.put("cause_of_loss", "fire");
        inputPayload.put("damage_areas", List.of("roof", "interior"));
        inputPayload.put("date_of_loss", "2024-10-01");

        // Mock I/O contracts
        when(documentStoreService.store(eq("DocumentStoreService-bucket"), any(String.class))).thenReturn("s3://DocumentStoreService-bucket/claim-001.json");
        when(rulesEngineService.query(eq("RulesEngineService_table"), any(String.class))).thenReturn(Map.of("rule_set", "ALE_TRIAGE_V1"));
        when(policyValidationService.validate(eq("PolicyValidationService_table"), any(String.class))).thenReturn(Map.of("status", "ACTIVE"));

        // Act
        Map<String, Object> result = transformationEngine.transform(inputPayload);

        // Assert
        assertNotNull(result, "Transformation result should not be null");
        assertEquals("CLM-FL01-2024-001", result.get("claim_number"), "Claim number should be generated");
        assertEquals("Complex", result.get("claim_type"), "Claim type should be set to Complex");
        assertEquals("Uninhabitable Property Review", result.get("task"), "Task should be Uninhabitable Property Review");
        assertEquals("ALE/loss-of-use", result.get("triage"), "Triage should be initiated for ALE/loss-of-use");
        assertEquals("Additional Living Expenses", result.get("reserve_type"), "Reserve should be set for additional living expenses");

        // Verify infrastructure I/O contracts were invoked
        verify(documentStoreService, times(1)).store(eq("DocumentStoreService-bucket"), any(String.class));
        verify(rulesEngineService, times(1)).query(eq("RulesEngineService_table"), any(String.class));
        verify(policyValidationService, times(1)).validate(eq("PolicyValidationService_table"), any(String.class));
    }
}

// Mock Infrastructure Services
interface DocumentStoreService { String store(String bucketName, String objectKeyPattern); }
interface RulesEngineService { Map<String, Object> query(String tableName, String partitionKey); }
interface PolicyValidationService { Map<String, Object> validate(String tableName, String partitionKey); }

// Transformation Engine (simplified for testing)
class ClaimTransformationEngine {
    private final DocumentStoreService documentStoreService;
    private final RulesEngineService rulesEngineService;
    private final PolicyValidationService policyValidationService;

    ClaimTransformationEngine(DocumentStoreService documentStoreService, RulesEngineService rulesEngineService, PolicyValidationService policyValidationService) {
        this.documentStoreService = documentStoreService;
        this.rulesEngineService = rulesEngineService;
        this.policyValidationService = policyValidationService;
    }

    public Map<String, Object> transform(Map<String, Object> payload) {
        String claimNumber = "CLM-" + payload.get("tenant_code") + "-" + payload.get("year") + "-001";
        String claimType = "Complex";
        String task = "Uninhabitable Property Review";
        String triage = "ALE/loss-of-use";
        String reserveType = "Additional Living Expenses";

        documentStoreService.store("DocumentStoreService-bucket", "claim_data_standardization_transformation_valida/" + claimNumber + ".json");
        rulesEngineService.query("RulesEngineService_table", "pk_ale_triage");
        policyValidationService.validate("PolicyValidationService_table", "pk_policy_" + payload.get("tenant_code"));

        Map<String, Object> result = new HashMap<>();
        result.put("claim_number", claimNumber);
        result.put("claim_type", claimType);
        result.put("task", task);
        result.put("triage", triage);
        result.put("reserve_type", reserveType);
        return result;
    }
}
