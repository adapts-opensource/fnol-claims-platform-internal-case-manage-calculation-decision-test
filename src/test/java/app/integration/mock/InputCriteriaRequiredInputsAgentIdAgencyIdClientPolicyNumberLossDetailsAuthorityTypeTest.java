package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MultiChannelFnolSubmissionOrchestrationValidationTest {

    @Mock
    private AgencyDirectoryService agencyDirectoryService;
    @Mock
    private AuthorityStatusService authorityStatusService;
    @Mock
    private LossDetailsSchemaValidator lossDetailsSchemaValidator;

    private FnolSubmissionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FnolSubmissionValidator(agencyDirectoryService, authorityStatusService, lossDetailsSchemaValidator);
    }

    @Test
    void input_criteria_required_inputs_agent_id_agency_id_client_policy_number_loss_details_authority_type_optional_inputs_aob_document_ref_attorney_rep_flag_communication_preference_input_validation_agent_id_must_be_active_in_agency_directory_authority_type_must_be_in_allowed_enumeration_loss_details_must_pass_schema_validation_freshness_requirements_agency_directory_must_be_1_hour_stale_authority_status_must_be_current() {
        // Arrange: Valid payload containing all required and optional inputs
        Map<String, Object> payload = Map.of(
            "agent_id", "AGT-123",
            "agency_id", "AGY-456",
            "client_policy_number", "POL-789",
            "loss_details", Map.of("type", "collision", "date", "2023-10-01"),
            "authority_type", "DIRECT",
            "aob_document_ref", "s3://bucket/doc.pdf",
            "attorney_rep_flag", true,
            "communication_preference", "EMAIL"
        );

        // Mock external I/O: Agency directory activity & freshness (< 1 hour stale)
        when(agencyDirectoryService.isActive("AGT-123")).thenReturn(true);
        when(agencyDirectoryService.getLastUpdated()).thenReturn(LocalDateTime.now().minusMinutes(10));

        // Mock external I/O: Authority status enumeration & freshness (current)
        when(authorityStatusService.isValid("DIRECT")).thenReturn(true);
        when(authorityStatusService.isCurrent("DIRECT")).thenReturn(true);

        // Mock external I/O: Schema validation
        when(lossDetailsSchemaValidator.validate(any(Map.class))).thenReturn(true);

        // Act & Assert: Orchestration should complete without throwing validation exceptions
        assertDoesNotThrow(() -> validator.validate(payload));

        // Verify orchestration correctly delegates to external contracts
        verify(agencyDirectoryService).isActive("AGT-123");
        verify(agencyDirectoryService).getLastUpdated();
        verify(authorityStatusService).isValid("DIRECT");
        verify(authorityStatusService).isCurrent("DIRECT");
        verify(lossDetailsSchemaValidator).validate(any(Map.class));
    }
}

// Supporting interfaces and SUT for self-contained mock testing
interface AgencyDirectoryService {
    boolean isActive(String agentId);
    LocalDateTime getLastUpdated();
}

interface AuthorityStatusService {
    boolean isValid(String authorityType);
    boolean isCurrent(String authorityType);
}

interface LossDetailsSchemaValidator {
    boolean validate(Map<String, Object> lossDetails);
}

class FnolSubmissionValidator {
    private final AgencyDirectoryService agencyDirectoryService;
    private final AuthorityStatusService authorityStatusService;
    private final LossDetailsSchemaValidator lossDetailsSchemaValidator;

    public FnolSubmissionValidator(AgencyDirectoryService agencyDirectoryService, AuthorityStatusService authorityStatusService, LossDetailsSchemaValidator lossDetailsSchemaValidator) {
        this.agencyDirectoryService = agencyDirectoryService;
        this.authorityStatusService = authorityStatusService;
        this.lossDetailsSchemaValidator = lossDetailsSchemaValidator;
    }

    public void validate(Map<String, Object> payload) {
        // Required inputs validation
        if (!payload.containsKey("agent_id") || !payload.containsKey("agency_id") ||
            !payload.containsKey("client_policy_number") || !payload.containsKey("loss_details") ||
            !payload.containsKey("authority_type")) {
            throw new IllegalArgumentException("Missing required inputs");
        }

        String agentId = (String) payload.get("agent_id");
        String authorityType = (String) payload.get("authority_type");
        Map<String, Object> lossDetails = (Map<String, Object>) payload.get("loss_details");

        // Validation: agent_id must be active in agency directory
        if (!agencyDirectoryService.isActive(agentId)) {
            throw new IllegalArgumentException("agent_id must be active in agency directory");
        }
        // Freshness: Agency directory must be < 1 hour stale
        if (LocalDateTime.now().isAfter(agencyDirectoryService.getLastUpdated().plusHours(1))) {
            throw new IllegalArgumentException("Agency directory must be < 1 hour stale");
        }

        // Validation: authority_type must be in allowed enumeration
        if (!authorityStatusService.isValid(authorityType)) {
            throw new IllegalArgumentException("authority_type must be in allowed enumeration");
        }
        // Freshness: Authority status must be current
        if (!authorityStatusService.isCurrent(authorityType)) {
            throw new IllegalArgumentException("Authority status must be current");
        }

        // Validation: loss_details must pass schema validation
        if (!lossDetailsSchemaValidator.validate(lossDetails)) {
            throw new IllegalArgumentException("loss_details must pass schema validation");
        }
    }
}
