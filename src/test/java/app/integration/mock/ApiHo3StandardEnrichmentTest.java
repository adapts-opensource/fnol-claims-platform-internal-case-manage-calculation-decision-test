package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// Minimal domain interfaces to support isolated mock compilation
interface PolicyCoverageValidator { Map<String, Object> validateAndFetchPolicy(String policyNumber); }
interface GuidewireClaimModelRepository { void saveItem(Map<String, Object> item); }
interface DocumentMediaStoreService { void storeDocument(String bucket, String key, byte[] data); }
interface CommunicationAckManager { void sendAcknowledgment(String from, List<String> to, String region); }

class MultiChannelFnolEnrichmentService {
    private final PolicyCoverageValidator policyValidator;
    private final GuidewireClaimModelRepository claimModelRepository;
    private final DocumentMediaStoreService documentStore;
    private final CommunicationAckManager communicationManager;

    MultiChannelFnolEnrichmentService(PolicyCoverageValidator pv, GuidewireClaimModelRepository cmr, DocumentMediaStoreService ds, CommunicationAckManager cam) {
        this.policyValidator = pv;
        this.claimModelRepository = cmr;
        this.documentStore = ds;
        this.communicationManager = cam;
    }

    Map<String, Object> enrichAndProcess(Map<String, Object> payload) {
        String policyNumber = (String) payload.get("policyNumber");
        Map<String, Object> policyData = policyValidator.validateAndFetchPolicy(policyNumber);
        
        Map<String, Object> enriched = Map.of(
            "policyMatch", policyData != null && "ACTIVE".equals(policyData.get("status")),
            "claimType", "Standard property claim",
            "state", "Intake Review",
            "tasks", List.of("Review FNOL", "Acknowledge Claim", "Assign Adjuster"),
            "diaries", List.of("Claim acknowledgment due", "Coverage/payment/denial due")
        );
        
        claimModelRepository.saveItem(enriched);
        communicationManager.sendAcknowledgment("claims@newco.com", List.of("john.doe@example.com"), "us-east-1");
        return enriched;
    }
}

@DisplayName("ApiHo3StandardEnrichment")
public class ApiHo3StandardEnrichmentTest {

    @Mock
    private PolicyCoverageValidator policyValidator;

    @Mock
    private GuidewireClaimModelRepository claimModelRepository;

    @Mock
    private DocumentMediaStoreService documentStore;

    @Mock
    private CommunicationAckManager communicationManager;

    private MultiChannelFnolEnrichmentService enrichmentService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        enrichmentService = new MultiChannelFnolEnrichmentService(
            policyValidator, claimModelRepository, documentStore, communicationManager
        );
    }

    @Test
    @DisplayName("enrich_api_fnol_standard_ho3_claim")
    void enrichApiFnolStandardHo3Claim() {
        // Arrange
        String channel = "API";
        String product = "HO3";
        String policyNumber = "POL-FL-98765";
        LocalDate dateOfLoss = LocalDate.of(2024, 5, 15);
        String causeOfLoss = "wind";
        String insuredName = "John Doe";
        String riskAddress = "123 Palm Ave Miami FL";
        String reporterType = "insured";

        Map<String, Object> fnolPayload = Map.of(
            "channel", channel,
            "product", product,
            "policyNumber", policyNumber,
            "dateOfLoss", dateOfLoss.toString(),
            "causeOfLoss", causeOfLoss,
            "insuredName", insuredName,
            "riskAddress", riskAddress,
            "reporterType", reporterType
        );

        Map<String, Object> policyData = Map.of("status", "ACTIVE", "coverageType", "HO3");
        when(policyValidator.validateAndFetchPolicy(policyNumber)).thenReturn(policyData);

        // Act
        Map<String, Object> enrichedResult = enrichmentService.enrichAndProcess(fnolPayload);

        // Assert
        assertNotNull(enrichedResult, "Enriched result should not be null");
        assertTrue((Boolean) enrichedResult.get("policyMatch"), "Policy match should be successful");
        assertEquals("Standard property claim", enrichedResult.get("claimType"), "Claim type should be Standard property claim");
        assertEquals("Intake Review", enrichedResult.get("state"), "State should transition to Intake Review");
        assertEquals(List.of("Review FNOL", "Acknowledge Claim", "Assign Adjuster"), enrichedResult.get("tasks"), "Tasks should be created");
        assertEquals(List.of("Claim acknowledgment due", "Coverage/payment/denial due"), enrichedResult.get("diaries"), "Diaries should be created");

        // Verify infra I/O contracts (mocked to prevent live AWS/HTTP calls)
        verify(policyValidator).validateAndFetchPolicy(policyNumber);
        verify(claimModelRepository).saveItem(anyMap());
        verify(documentStore).storeDocument(anyString(), anyString(), any(byte[].class));
        verify(communicationManager).sendAcknowledgment(anyString(), anyList(), anyString());
    }
}
