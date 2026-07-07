package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

interface DocumentStoreService {
    String storeObject(String bucketName, String objectKeyPattern, Map<String, Object> payload);
}

interface PolicyValidationService {
    Optional<Map<String, Object>> queryItem(String tableName, String partitionKey, String pkValue);
}

interface RulesEngineService {
    Optional<Map<String, Object>> queryItem(String tableName, String partitionKey, String ruleId);
}

class ClaimDataStandardizationService {
    private final DocumentStoreService documentStoreService;
    private final PolicyValidationService policyValidationService;
    private final RulesEngineService rulesEngineService;

    ClaimDataStandardizationService(DocumentStoreService documentStoreService,
                                    PolicyValidationService policyValidationService,
                                    RulesEngineService rulesEngineService) {
        this.documentStoreService = documentStoreService;
        this.policyValidationService = policyValidationService;
        this.rulesEngineService = rulesEngineService;
    }

    String processClaimData(Map<String, Object> payload) {
        String uri = documentStoreService.storeObject("DocumentStoreService-bucket", "DocumentStoreService/{entity_id}.json", payload);
        String policyId = (String) payload.getOrDefault("taskId", "DEFAULT-POLICY");
        policyValidationService.queryItem("PolicyValidationService_table", "pk", policyId);
        rulesEngineService.queryItem("RulesEngineService_table", "pk", "COV-001");
        return uri;
    }
}

@ExtendWith(MockitoExtension.class)
public class InputCriteriaTaskIdMatchCandidatesDolPolicyTest {

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private RulesEngineService rulesEngineService;

    @InjectMocks
    private ClaimDataStandardizationService claimDataStandardizationService;

    private static final String TASK_ID = "TASK-12345";
    private static final String POLICY_ID = "POL-67890";
    private static final String DOL = "2023-10-15";
    private static final String BUCKET_NAME = "DocumentStoreService-bucket";
    private static final String OBJECT_KEY_PATTERN = "DocumentStoreService/{entity_id}.json";
    private static final String TABLE_NAME = "PolicyValidationService_table";
    private static final String PARTITION_KEY = "pk";
    private static final String RULES_TABLE_NAME = "RulesEngineService_table";

    @BeforeEach
    void setUp() {
        // Initialize or reset mock states if required by broader test suite
    }

    @Test
    void input_criteria_task_id_match_candidates_dol_policy_period_details_coverage_rules() {
        // Arrange
        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("taskId", TASK_ID);
        inputPayload.put("matchCandidates", Collections.singletonList("CAND-001"));
        inputPayload.put("dateOfLoss", DOL);
        inputPayload.put("policyPeriodDetails", Map.of("startDate", "2023-01-01", "endDate", "2024-01-01"));
        inputPayload.put("coverageRules", Map.of("autoLiability", true, "collision", false));

        String expectedUri = "s3://" + BUCKET_NAME + "/DocumentStoreService/entity-id.json";
        when(documentStoreService.storeObject(eq(BUCKET_NAME), eq(OBJECT_KEY_PATTERN), anyMap()))
                .thenReturn(expectedUri);

        Map<String, Object> policyItem = Map.of("pk", POLICY_ID, "status", "ACTIVE", "effectiveDate", "2023-01-01");
        when(policyValidationService.queryItem(eq(TABLE_NAME), eq(PARTITION_KEY), eq(POLICY_ID)))
                .thenReturn(Optional.of(policyItem));

        Map<String, Object> ruleItem = Map.of("ruleId", "COV-001", "type", "AUTO", "enabled", true);
        when(rulesEngineService.queryItem(eq(RULES_TABLE_NAME), eq(PARTITION_KEY), eq("COV-001")))
                .thenReturn(Optional.of(ruleItem));

        // Act
        String resultUri = claimDataStandardizationService.processClaimData(inputPayload);

        // Assert
        assertNotNull(resultUri, "Service should return a valid object URI after processing");
        assertEquals(expectedUri, resultUri);

        verify(documentStoreService).storeObject(eq(BUCKET_NAME), eq(OBJECT_KEY_PATTERN), anyMap());
        verify(policyValidationService).queryItem(eq(TABLE_NAME), eq(PARTITION_KEY), eq(POLICY_ID));
        verify(rulesEngineService).queryItem(eq(RULES_TABLE_NAME), eq(PARTITION_KEY), eq("COV-001"));
    }
}
