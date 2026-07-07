package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DateValidationStatusTest {

    @Mock
    private ClaimDataStoreService claimDataStoreService;

    @Mock
    private DocumentManagementService documentManagementService;

    @Mock
    private DateValidationService dateValidationService;

    @InjectMocks
    private ClaimDataStandardizationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        // Reset mocks and prepare thread-safe test environment
    }

    @Test
    void dateValidationStatus() {
        String claimId = "CLM-12345";
        Map<String, Object> payload = new HashMap<>();
        payload.put("dateOfLoss", "2023-10-01");
        payload.put("dateOfReport", "2023-10-02");

        when(dateValidationService.validateDates(payload)).thenReturn("VALID");
        when(claimDataStoreService.saveClaimData(claimId, payload)).thenReturn(Map.of("status", "SUCCESS"));

        Map<String, Object> result = orchestrator.processClaimData(claimId, payload);

        assertEquals("SUCCESS", result.get("status"));
        assertEquals("VALID", result.get("dateValidationStatus"));
        verify(dateValidationService).validateDates(payload);
        verify(claimDataStoreService).saveClaimData(claimId, payload);
    }

    @Test
    void dateValidationStatus_invalidDatesReturnsFailure() {
        String claimId = "CLM-67890";
        Map<String, Object> payload = new HashMap<>();
        payload.put("dateOfLoss", "invalid-date");
        payload.put("dateOfReport", "2023-10-02");

        when(dateValidationService.validateDates(payload)).thenReturn("INVALID");

        Map<String, Object> result = orchestrator.processClaimData(claimId, payload);

        assertEquals("FAILURE", result.get("status"));
        assertEquals("INVALID", result.get("dateValidationStatus"));
        verify(dateValidationService).validateDates(payload);
        verifyNoInteractions(claimDataStoreService);
    }

    @Test
    void dateValidationStatus_missingDatesReturnsPending() {
        String claimId = "CLM-99999";
        Map<String, Object> payload = new HashMap<>();
        payload.put("dateOfReport", "2023-10-02");

        when(dateValidationService.validateDates(payload)).thenReturn("PENDING");

        Map<String, Object> result = orchestrator.processClaimData(claimId, payload);

        assertEquals("PENDING", result.get("status"));
        assertEquals("PENDING", result.get("dateValidationStatus"));
        verify(dateValidationService).validateDates(payload);
    }
}

// Stub dependencies for compilation context. In production these interface with DynamoDB/S3.
interface ClaimDataStoreService {
    Map<String, Object> saveClaimData(String id, Map<String, Object> payload);
}

interface DocumentManagementService {
    String uploadDocument(Map<String, Object> payload);
}

interface DateValidationService {
    String validateDates(Map<String, Object> payload);
}

class ClaimDataStandardizationOrchestrator {
    private final ClaimDataStoreService claimDataStoreService;
    private final DocumentManagementService documentManagementService;
    private final DateValidationService dateValidationService;

    public ClaimDataStandardizationOrchestrator(ClaimDataStoreService claimDataStoreService,
                                                DocumentManagementService documentManagementService,
                                                DateValidationService dateValidationService) {
        this.claimDataStoreService = claimDataStoreService;
        this.documentManagementService = documentManagementService;
        this.dateValidationService = dateValidationService;
    }

    public Map<String, Object> processClaimData(String id, Map<String, Object> payload) {
        String validationStatus = dateValidationService.validateDates(payload);
        payload.put("dateValidationStatus", validationStatus);

        Map<String, Object> result = new HashMap<>();
        if ("VALID".equals(validationStatus)) {
            result.put("status", "SUCCESS");
            claimDataStoreService.saveClaimData(id, payload);
        } else if ("INVALID".equals(validationStatus)) {
            result.put("status", "FAILURE");
        } else {
            result.put("status", "PENDING");
        }
        return result;
    }
}
