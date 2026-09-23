package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationEnrichmentValidationTest {

    @Mock
    private ClaimEnrichmentValidationProcessor validationProcessor;

    @Mock
    private TaskCreationService taskCreationService;

    private ClaimStandardizationEnrichmentValidationService service;

    @BeforeEach
    void setUp() {
        service = new ClaimStandardizationEnrichmentValidationService(validationProcessor, taskCreationService);
    }

    @Test
    void ambiguousMatchesAlwaysCreateResolvePolicyMatchTask() {
        String claimId = "CLM-AMB-001";
        Map<String, Object> payload = Map.of(
            "id", claimId,
            "enrichmentStatus", "COMPLETED",
            "ambiguousMatches", true,
            "matchCandidates", Map.of("POL-001", 0.75, "POL-002", 0.73)
        );

        service.validateAndEnrich(claimId, payload);

        verify(taskCreationService, times(1))
            .createTask(eq("RESOLVE_POLICY_MATCH"), eq(claimId), any());
    }
}

class ClaimStandardizationEnrichmentValidationService {
    private final ClaimEnrichmentValidationProcessor validationProcessor;
    private final TaskCreationService taskCreationService;

    ClaimStandardizationEnrichmentValidationService(
            ClaimEnrichmentValidationProcessor validationProcessor,
            TaskCreationService taskCreationService) {
        this.validationProcessor = validationProcessor;
        this.taskCreationService = taskCreationService;
    }

    void validateAndEnrich(String claimId, Map<String, Object> payload) {
        validationProcessor.process(payload);
        if (Boolean.TRUE.equals(payload.get("ambiguousMatches"))) {
            taskCreationService.createTask("RESOLVE_POLICY_MATCH", claimId, payload);
        }
    }
}

interface ClaimEnrichmentValidationProcessor {
    void process(Map<String, Object> payload);
}

interface TaskCreationService {
    void createTask(String taskType, String entityId, Map<String, Object> payload);
}
