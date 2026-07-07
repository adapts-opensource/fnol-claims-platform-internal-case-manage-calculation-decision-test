package app.integration.mock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TriageRulesOrchestrationTest {

    @Mock
    private RulesTriageService rulesTriageService;
    @Mock
    private ClaimDataStoreService claimDataStoreService;
    @Mock
    private DocumentManagementService documentManagementService;
    @Mock
    private StructuredLogger logger;

    @InjectMocks
    private ClaimDataStandardizationOrchestration orchestration;

    private static final String CLAIM_ID = "claim-123";
    private static final String RULES_TABLE = "Rules & Triage Service_table";
    private static final String PK = "pk";
    private static final String BUCKET = "Document Management-bucket";

    @BeforeEach
    void setUp() {
        // MockitoExtension handles mock initialization and lifecycle
    }

    @Test
    void triage_rules_execute_successfully() {
        // Arrange
        Map<String, Object> inputPayload = Map.of("claimType", "AUTO", "severity", "MEDIUM");
        Map<String, Object> triageResult = Map.of("category", "STANDARD", "assignedTeam", "AUTO_Triage");
        Map<String, String> infraReferences = Map.of("docUri", "s3://Document Management-bucket/claim-123.json");

        when(rulesTriageService.fetchRules(eq(RULES_TABLE), eq(PK), eq(CLAIM_ID))).thenReturn(triageResult);
        when(claimDataStoreService.putItem(eq("Claim Data Store_table"), eq(PK), eq(CLAIM_ID), any(Map.class))).thenReturn(true);
        when(documentManagementService.writeObject(eq(BUCKET), eq("claim-123.json"), any(byte[].class))).thenReturn("s3://" + BUCKET + "/claim-123.json");

        // Act
        Map<String, Object> resultPayload = orchestration.executeTriageRules(CLAIM_ID, inputPayload);

        // Assert
        assertNotNull(resultPayload, "Orchestration should return a non-null payload");
        assertEquals("STANDARD", resultPayload.get("category"), "Triage category should be set correctly");
        assertEquals("AUTO_Triage", resultPayload.get("assignedTeam"), "Assigned team should be populated");
        assertTrue(resultPayload.containsKey("docUri"), "Document URI reference should be present");

        // Verify infrastructure I/O contracts
        verify(rulesTriageService, times(1)).fetchRules(eq(RULES_TABLE), eq(PK), eq(CLAIM_ID));
        verify(claimDataStoreService, times(1)).putItem(eq("Claim Data Store_table"), eq(PK), eq(CLAIM_ID), any(Map.class));
        verify(documentManagementService, times(1)).writeObject(eq(BUCKET), eq("claim-123.json"), any(byte[].class));
        verify(logger, times(1)).info(eq("Triage rules executed successfully"), eq(CLAIM_ID));
    }
}
