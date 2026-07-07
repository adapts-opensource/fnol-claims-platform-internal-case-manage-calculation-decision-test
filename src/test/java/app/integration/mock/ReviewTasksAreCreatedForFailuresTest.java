package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReviewTasksAreCreatedForFailuresTest {

    @Mock
    private ClaimCalculationTransformationService transformationService;

    @Mock
    private WorkflowTaskRouterDynamoDB taskRouter;

    @Mock
    private AuditDiaryStoreS3 auditStore;

    @BeforeEach
    void setUp() {
        // MockitoExtension automatically initializes and injects mocks
    }

    @Test
    void review_tasks_are_created_for_failures() {
        // Given: A claim payload triggering a transformation failure
        String claimId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of("claimType", "AUTO", "amount", 5000.0);
        when(transformationService.transform(claimId, payload)).thenThrow(new IllegalStateException("Calculation transformation failed"));

        ClaimDataStandardizationProcessor processor = new ClaimDataStandardizationProcessor(transformationService, taskRouter, auditStore);

        // When & Then: Processor should throw, but side effects must be verified
        assertThrows(IllegalStateException.class, () -> processor.process(claimId, payload));

        // Verify: Review task created in WorkflowTaskRouter_dynamodb
        verify(taskRouter, times(1)).putItem(eq("WorkflowTaskRouter_table"), anyMap());
        // Verify: Audit log written to AuditDiaryStore_s3
        verify(auditStore, times(1)).putObject(eq("AuditDiaryStore-bucket"), eq("AuditDiaryStore/" + claimId + ".json"), anyString());
    }

    // Mock interfaces matching infra_io_contracts
    interface ClaimCalculationTransformationService {
        void transform(String id, Map<String, Object> payload);
    }

    interface WorkflowTaskRouterDynamoDB {
        void putItem(String tableName, Map<String, Object> itemPayload);
    }

    interface AuditDiaryStoreS3 {
        void putObject(String bucketName, String objectKey, String body);
    }

    // Processor under test
    static class ClaimDataStandardizationProcessor {
        private final ClaimCalculationTransformationService transformationService;
        private final WorkflowTaskRouterDynamoDB taskRouter;
        private final AuditDiaryStoreS3 auditStore;

        ClaimDataStandardizationProcessor(ClaimCalculationTransformationService transformationService,
                                          WorkflowTaskRouterDynamoDB taskRouter,
                                          AuditDiaryStoreS3 auditStore) {
            this.transformationService = transformationService;
            this.taskRouter = taskRouter;
            this.auditStore = auditStore;
        }

        void process(String id, Map<String, Object> payload) {
            try {
                transformationService.transform(id, payload);
            } catch (Exception e) {
                taskRouter.putItem("WorkflowTaskRouter_table", Map.of("pk", "REVIEW:" + id, "taskType", "CLAIM_CALCULATION_REVIEW", "status", "PENDING", "error", e.getMessage()));
                auditStore.putObject("AuditDiaryStore-bucket", "AuditDiaryStore/" + id + ".json", e.getMessage());
                throw e;
            }
        }
    }
}
