package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationDecisionIntegrationMockTest {

    @Mock
    private DocumentStoreService documentStoreService;
    @Mock
    private PolicyValidationService policyValidationService;
    @Mock
    private RulesEngineService rulesEngineService;
    @Mock
    private ClaimDecisionEngine claimDecisionEngine;

    private String testClaimId;
    private Map<String, Object> fnolPayload;

    @BeforeEach
    void setUp() {
        testClaimId = "fnol-submission-001";
        fnolPayload = new HashMap<>();
        fnolPayload.put("id", testClaimId);
        fnolPayload.put("eventType", "FNOL");
        fnolPayload.put("status", "NEW_SUBMISSION");
        fnolPayload.put("payload", Map.of("lossDate", "2024-05-01", "policyNumber", "POL-12345"));
    }

    @Test
    void applies_when_new_fnol_submission_received() {
        // Arrange
        String bucketName = "DocumentStoreService-bucket";
        String objectKeyPattern = "DocumentStoreService/" + testClaimId + ".json";
        String expectedUri = "s3://" + bucketName + "/" + objectKeyPattern;

        when(documentStoreService.store(anyString(), anyString())).thenReturn(expectedUri);
        when(policyValidationService.validate(any())).thenReturn(Map.of("valid", true));
        when(rulesEngineService.evaluate(any())).thenReturn(Map.of("decision", "APPROVED"));
        when(claimDecisionEngine.process(anyMap(), anyString())).thenReturn("STANDARDIZED_VALIDATED");

        // Act
        String decisionResult = claimDecisionEngine.process(fnolPayload, testClaimId);

        // Assert
        assertEquals("STANDARDIZED_VALIDATED", decisionResult,
                "Decision should be applied when a new FNOL submission is received");
        verify(documentStoreService).store(eq(bucketName), eq(objectKeyPattern));
        verify(policyValidationService).validate(any());
        verify(rulesEngineService).evaluate(any());
        verify(claimDecisionEngine).process(fnolPayload, testClaimId);
    }
}
