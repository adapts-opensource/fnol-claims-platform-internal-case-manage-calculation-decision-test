package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationStateTransitionOrchestrationTest {

    @Mock
    private ClaimDataStoreClient claimDataStoreClient;
    @Mock
    private DocumentManagementClient documentManagementClient;
    @Mock
    private InputValidationService inputValidationService;
    @Mock
    private FreshnessCheckService freshnessCheckService;

    @InjectMocks
    private ClaimDataStandardizationOrchestrator orchestrator;

    private Map<String, Object> validPayload;

    @BeforeEach
    void setUp() {
        validPayload = new HashMap<>();
        validPayload.put("policy_number", "POL-123456-ABC");
        validPayload.put("risk_address", "123 Main St, Anytown, CA 90210");
        validPayload.put("date_of_loss", LocalDateTime.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        validPayload.put("cause_of_loss", "fire");
        validPayload.put("named_insured_identity", "John Doe");
        validPayload.put("tenant_landlord_relationship", "owner");
        validPayload.put("occupancy_type", "residential");
        validPayload.put("catastrophe_event_id", "CAT-2023-001");

        // Default mock behaviors
        when(inputValidationService.validatePolicyNumber(anyString())).thenReturn(true);
        when(inputValidationService.validateRiskAddress(anyString())).thenReturn(true);
        when(freshnessCheckService.checkPolicyDataFreshness(any())).thenReturn(true);
        when(freshnessCheckService.checkCatastropheMoratoriumStatus(any(), any())).thenReturn(true);
        when(claimDataStoreClient.saveItem(anyString(), anyMap())).thenReturn("item-id");
        when(documentManagementClient.writeObject(anyString(), anyString(), anyMap())).thenReturn("s3://bucket/key");
    }

    @Test
    void testRequiredInputsMissing() {
        Map<String, Object> payload = new HashMap<>(validPayload);
        payload.remove("policy_number");
        payload.remove("risk_address");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> orchestrator.orchestrate("orch-123", payload));
        assertEquals("Missing required inputs: [policy_number, risk_address]", exception.getMessage());
        verifyNoInteractions(claimDataStoreClient, documentManagementClient);
    }

    @Test
    void testOptionalInputsIncluded() {
        Map<String, Object> payload = new HashMap<>(validPayload);
        payload.put("tenant_landlord_relationship", "tenant");
        payload.put("occupancy_type", "commercial");
        payload.put("catastrophe_event_id", "CAT-2024-002");

        String result = orchestrator.orchestrate("orch-456", payload);
        assertEquals("orch-456", result);
        verify(inputValidationService, times(1)).validatePolicyNumber("POL-123456-ABC");
        verify(freshnessCheckService, times(1)).checkCatastropheMoratoriumStatus("CAT-2024-002", payload.get("date_of_loss"));
    }

    @Test
    void testInvalidPolicyNumberFormatPerPAS() {
        Map<String, Object> payload = new HashMap<>(validPayload);
        payload.put("policy_number", "INVALID-FORMAT");
        when(inputValidationService.validatePolicyNumber("INVALID-FORMAT")).thenReturn(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> orchestrator.orchestrate("orch-789", payload));
        assertEquals("Input validation failed: policy_number must match format per PAS schema", exception.getMessage());
        verifyNoInteractions(claimDataStoreClient, documentManagementClient);
    }

    @Test
    void testFutureDateOfLoss() {
        Map<String, Object> payload = new HashMap<>(validPayload);
        payload.put("date_of_loss", LocalDateTime.now().plusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> orchestrator.orchestrate("orch-001", payload));
        assertEquals("Input validation failed: date_of_loss must be ISO 8601 and not in the future", exception.getMessage());
        verifyNoInteractions(claimDataStoreClient, documentManagementClient);
    }

    @Test
    void testInvalidRiskAddressUSPSValidation() {
        Map<String, Object> payload = new HashMap<>(validPayload);
        payload.put("risk_address", "123 NONEXISTENT ST");
        when(inputValidationService.validateRiskAddress("123 NONEXISTENT ST")).thenReturn(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> orchestrator.orchestrate("orch-002", payload));
        assertEquals("Input validation failed: risk_address must pass USPS validation", exception.getMessage());
        verifyNoInteractions(claimDataStoreClient, documentManagementClient);
    }

    @Test
    void testPolicyDataStaleBeyond24Hours() {
        Map<String, Object> payload = new HashMap<>(validPayload);
        when(freshnessCheckService.checkPolicyDataFreshness(payload.get("date_of_loss"))).thenReturn(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> orchestrator.orchestrate("orch-003", payload));
        assertEquals("Freshness requirement failed: Policy data must be fetched within last 24 hours or during intake session", exception.getMessage());
        verifyNoInteractions(claimDataStoreClient, documentManagementClient);
    }

    @Test
    void testCatastropheMoratoriumStatusExpired() {
        Map<String, Object> payload = new HashMap<>(validPayload);
        when(freshnessCheckService.checkCatastropheMoratoriumStatus("CAT-2023-001", payload.get("date_of_loss"))).thenReturn(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> orchestrator.orchestrate("orch-004", payload));
        assertEquals("Freshness requirement failed: Catastrophe moratorium status must be current as of DoL", exception.getMessage());
        verifyNoInteractions(claimDataStoreClient, documentManagementClient);
    }

    @Test
    void testSuccessfulOrchestration() {
        String result = orchestrator.orchestrate("orch-success", validPayload);
        assertEquals("orch-success", result);

        verify(inputValidationService, times(1)).validatePolicyNumber("POL-123456-ABC");
        verify(inputValidationService, times(1)).validateRiskAddress("123 Main St, Anytown, CA 90210");
        verify(freshnessCheckService, times(1)).checkPolicyDataFreshness(validPayload.get("date_of_loss"));
        verify(freshnessCheckService, times(1)).checkCatastropheMoratoriumStatus("CAT-2023-001", validPayload.get("date_of_loss"));
        verify(claimDataStoreClient, times(1)).saveItem(eq("orch-success"), anyMap());
        verify(documentManagementClient, times(1)).writeObject(anyString(), anyString(), anyMap());
    }

    // Minimal system under test and dependencies for mock isolation
    static class ClaimDataStandardizationOrchestrator {
        private final ClaimDataStoreClient claimDataStoreClient;
        private final DocumentManagementClient documentManagementClient;
        private final InputValidationService inputValidationService;
        private final FreshnessCheckService freshnessCheckService;

        ClaimDataStandardizationOrchestrator(ClaimDataStoreClient claimDataStoreClient,
                                             DocumentManagementClient documentManagementClient,
                                             InputValidationService inputValidationService,
                                             FreshnessCheckService freshnessCheckService) {
            this.claimDataStoreClient = claimDataStoreClient;
            this.documentManagementClient = documentManagementClient;
            this.inputValidationService = inputValidationService;
            this.freshnessCheckService = freshnessCheckService;
        }

        String orchestrate(String id, Map<String, Object> payload) {
            String[] required = {"policy_number", "risk_address", "date_of_loss", "cause_of_loss", "named_insured_identity"};
            for (String req : required) {
                if (!payload.containsKey(req)) {
                    throw new IllegalArgumentException("Missing required inputs: [" + String.join(", ", required) + "]");
                }
            }

            if (!inputValidationService.validatePolicyNumber((String) payload.get("policy_number"))) {
                throw new IllegalArgumentException("Input validation failed: policy_number must match format per PAS schema");
            }
            if (!inputValidationService.validateRiskAddress((String) payload.get("risk_address"))) {
                throw new IllegalArgumentException("Input validation failed: risk_address must pass USPS validation");
            }
            String dol = (String) payload.get("date_of_loss");
            if (LocalDateTime.parse(dol, DateTimeFormatter.ISO_LOCAL_DATE_TIME).isAfter(LocalDateTime.now())) {
                throw new IllegalArgumentException("Input validation failed: date_of_loss must be ISO 8601 and not in the future");
            }
            if (!freshnessCheckService.checkPolicyDataFreshness(payload.get("date_of_loss"))) {
                throw new IllegalArgumentException("Freshness requirement failed: Policy data must be fetched within last 24 hours or during intake session");
            }
            if (payload.containsKey("catastrophe_event_id")) {
                if (!freshnessCheckService.checkCatastropheMoratoriumStatus((String) payload.get("catastrophe_event_id"), payload.get("date_of_loss"))) {
                    throw new IllegalArgumentException("Freshness requirement failed: Catastrophe moratorium status must be current as of DoL");
                }
            }

            claimDataStoreClient.saveItem(id, payload);
            documentManagementClient.writeObject("Document Management-bucket", "Document Management/" + id + ".json", payload);
            return id;
        }
    }

    interface ClaimDataStoreClient {
        String saveItem(String id, Map<String, Object> item);
    }

    interface DocumentManagementClient {
        String writeObject(String bucket, String key, Map<String, Object> data);
    }

    interface InputValidationService {
        boolean validatePolicyNumber(String policyNumber);
        boolean validateRiskAddress(String address);
    }

    interface FreshnessCheckService {
        boolean checkPolicyDataFreshness(Object dateOfLoss);
        boolean checkCatastropheMoratoriumStatus(String eventId, Object dateOfLoss);
    }
}
