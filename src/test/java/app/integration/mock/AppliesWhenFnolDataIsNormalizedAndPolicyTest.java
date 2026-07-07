package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationCalculationTransformTest {

    @Mock
    private AuditDiaryStore auditDiaryStore;

    @Mock
    private RulesEngineDecisionService rulesEngine;

    @Mock
    private ClaimCalculationTransformer transformer;

    private String testClaimId;
    private Map<String, Object> normalizedFnolData;
    private Map<String, Object> matchedPolicy;

    @BeforeEach
    void setUp() {
        testClaimId = UUID.randomUUID().toString();
        normalizedFnolData = Map.of(
                "claimType", "AUTO",
                "incidentDate", "2023-10-01",
                "damageAmount", 5000.0,
                "normalized", true
        );
        matchedPolicy = Map.of(
                "policyNumber", "POL-12345",
                "coverageType", "COMPREHENSIVE",
                "deductible", 500.0,
                "policyStatus", "ACTIVE"
        );
    }

    @Test
    void applies_when_fnol_data_is_normalized_and_policy_matched() {
        // Arrange
        Map<String, Object> expectedPayload = Map.of(
                "id", testClaimId,
                "standardizedClaimType", "AUTO",
                "calculatedDeductible", 500.0,
                "estimatedPayout", 4500.0,
                "transformationStatus", "SUCCESS",
                "fnolNormalized", true,
                "policyMatched", true
        );

        when(transformer.transform(eq(normalizedFnolData), eq(matchedPolicy)))
                .thenReturn(expectedPayload);

        // Act
        Map<String, Object> actualPayload = transformer.transform(normalizedFnolData, matchedPolicy);
        auditDiaryStore.log(testClaimId, actualPayload);

        // Assert
        assertNotNull(actualPayload);
        assertEquals(testClaimId, actualPayload.get("id"));
        assertEquals("SUCCESS", actualPayload.get("transformationStatus"));
        assertTrue((Boolean) actualPayload.get("fnolNormalized"));
        assertTrue((Boolean) actualPayload.get("policyMatched"));
        assertEquals(4500.0, actualPayload.get("estimatedPayout"));

        verify(transformer).transform(normalizedFnolData, matchedPolicy);
        verify(auditDiaryStore).log(testClaimId, actualPayload);
    }

    // Mockable external I/O contracts aligned with infra_io_contracts
    interface AuditDiaryStore {
        void log(String entityId, Map<String, Object> payload);
    }

    interface RulesEngineDecisionService {
        Map<String, Object> queryRules(String tableName, String partitionKey);
    }

    interface ClaimCalculationTransformer {
        Map<String, Object> transform(Map<String, Object> normalizedFnol, Map<String, Object> matchedPolicy);
    }
}
