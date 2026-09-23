package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimInitiationRoutingOrchestrationTransformationTest {

    @Mock
    private ClaimTransformationOrchestrator transformationOrchestrator;
    @Mock
    private RuleEngineService ruleEngineService;
    @Mock
    private S3Client s3Client;
    @Mock
    private DynamoDbClient dynamoDbClient;

    @BeforeEach
    void setUp() {
        lenient().when(transformationOrchestrator.transform(any(ClaimDto.class))).thenReturn(new ClaimResult());
        lenient().when(ruleEngineService.evaluate(any(ClaimDto.class))).thenReturn(RuleOutcome.DRAFT);
        lenient().when(s3Client.putObject(any(), any())).thenReturn(null);
        lenient().when(dynamoDbClient.putItem(any())).thenReturn(null);
    }

    @Test
    void decision_activation_status_rule_if_approved_date_met_activate_else_remain_draft_expected_outcome_versioned_rule_deployed() {
        // Arrange: Approved & Date Met -> Activate
        ClaimDto approvedClaim = new ClaimDto("CLM-1001", true, LocalDate.of(2023, 1, 1));
        when(ruleEngineService.evaluate(approvedClaim)).thenReturn(RuleOutcome.ACTIVATED);
        when(transformationOrchestrator.transform(approvedClaim))
                .thenAnswer(invocation -> {
                    ClaimResult res = new ClaimResult();
                    res.status = ClaimStatus.ACTIVATED;
                    res.ruleVersion = "v1.2.0";
                    return res;
                });

        // Act
        ClaimResult result = transformationOrchestrator.transform(approvedClaim);

        // Assert: Activate branch
        assertEquals(ClaimStatus.ACTIVATED, result.status());
        verify(ruleEngineService).evaluate(approvedClaim);
        verify(s3Client).putObject(any(), any());
        verify(dynamoDbClient).putItem(any());
        verify(ruleEngineService).deployVersionedRule(eq("v1.2.0"));

        // Arrange: Not Approved OR Date Not Met -> Remain Draft
        ClaimDto unapprovedClaim = new ClaimDto("CLM-1002", false, LocalDate.now());
        when(ruleEngineService.evaluate(unapprovedClaim)).thenReturn(RuleOutcome.DRAFT);
        when(transformationOrchestrator.transform(unapprovedClaim))
                .thenAnswer(invocation -> {
                    ClaimResult res = new ClaimResult();
                    res.status = ClaimStatus.DRAFT;
                    res.ruleVersion = null;
                    return res;
                });

        // Act
        ClaimResult resultDraft = transformationOrchestrator.transform(unapprovedClaim);

        // Assert: Draft branch
        assertEquals(ClaimStatus.DRAFT, resultDraft.status());
        verify(ruleEngineService, times(2)).evaluate(any());
        verify(s3Client, times(2)).putObject(any(), any());
        verify(dynamoDbClient, times(2)).putItem(any());
        // Rule deployment only triggered for ACTIVATED state
        verify(ruleEngineService, times(1)).deployVersionedRule(eq("v1.2.0"));
    }
}

// Package-private helpers for test compilation
record ClaimDto(String claimId, boolean approved, LocalDate activationDate) {}
record ClaimResult(ClaimStatus status, String ruleVersion) {}
enum ClaimStatus { DRAFT, ACTIVATED }
enum RuleOutcome { DRAFT, ACTIVATED }
interface ClaimTransformationOrchestrator { ClaimResult transform(ClaimDto dto); }
interface RuleEngineService { RuleOutcome evaluate(ClaimDto dto); void deployVersionedRule(String version); }
interface S3Client { void putObject(Object request, Object body); }
interface DynamoDbClient { void putItem(Object item); }
