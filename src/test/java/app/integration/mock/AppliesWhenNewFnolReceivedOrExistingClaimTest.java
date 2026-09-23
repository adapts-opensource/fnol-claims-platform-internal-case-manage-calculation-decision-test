package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Mock infrastructure contracts aligned with NewCo Insurance data & comms layers
interface MockDynamoDbPersistence {
    void putItem(String table, String pk, Map<String, Object> item);
    boolean exists(String table, String pk);
}

interface MockSesCommunication {
    String sendEmail(String from, String[] to, String region);
}

interface MockS3DocumentStore {
    String putObject(String bucket, String key, String content);
}

// Core orchestration decision engine under test
class InsuredEngagementDecisionOrchestrator {
    private final MockDynamoDbPersistence persistence;
    private final MockSesCommunication communication;
    private final MockS3DocumentStore documentStore;
    private final Map<String, Boolean> decisionApplied = new ConcurrentHashMap<>();

    InsuredEngagementDecisionOrchestrator(MockDynamoDbPersistence persistence,
                                          MockSesCommunication communication,
                                          MockS3DocumentStore documentStore) {
        this.persistence = persistence;
        this.communication = communication;
        this.documentStore = documentStore;
    }

    void evaluateEngagementDecision(String claimId, String eventType) {
        // Input validation NFR
        if (claimId == null || eventType == null || claimId.isBlank() || eventType.isBlank()) {
            throw new IllegalArgumentException("Claim ID and event type must not be null or empty");
        }

        boolean isNewFnol = "NEW_FNOL".equalsIgnoreCase(eventType);
        boolean isExistingClaim = "CLAIM_UPDATED".equalsIgnoreCase(eventType);

        if (isNewFnol || isExistingClaim) {
            boolean exists = persistence.exists("Claims", claimId);
            if (isNewFnol && !exists) {
                persistence.putItem("Claims", claimId, Map.of("status", "OPEN", "type", "FNOL"));
            } else if (isExistingClaim && exists) {
                persistence.putItem("Claims", claimId, Map.of("status", "UPDATED"));
            }

            // Structured logging placeholder (NFR: observability)
            // logger.info("Decision applied for claimId={}, eventType={}", claimId, eventType);

            communication.sendEmail("claims@newco.com", new String[]{claimId + "@newco.com"}, "us-east-1");
            documentStore.putObject("document-store", "claims/" + claimId + ".json", "{}");
            decisionApplied.put(claimId, true);
        }
    }

    boolean isDecisionApplied(String claimId) {
        return Boolean.TRUE.equals(decisionApplied.get(claimId));
    }
}

class InsuredEngagementOrchestrationDecisionTest {
    private InsuredEngagementDecisionOrchestrator orchestrator;
    private MockDynamoDbPersistence mockDb;
    private MockSesCommunication mockSes;
    private MockS3DocumentStore mockS3;

    @BeforeEach
    void setUp() {
        mockDb = new MockDynamoDbPersistence() {
            private final Map<String, Map<String, Object>> store = new ConcurrentHashMap<>();
            public void putItem(String table, String pk, Map<String, Object> item) { store.put(pk, item); }
            public boolean exists(String table, String pk) { return store.containsKey(pk); }
        };
        mockSes = new MockSesCommunication() {
            public String sendEmail(String from, String[] to, String region) { return "msg-id-" + System.nanoTime(); }
        };
        mockS3 = new MockS3DocumentStore() {
            public String putObject(String bucket, String key, String content) { return "s3://" + bucket + "/" + key; }
        };
        orchestrator = new InsuredEngagementDecisionOrchestrator(mockDb, mockSes, mockS3);
    }

    @Test
    void applies_when_new_fnol_received_or_existing_claim_updated() {
        // Scenario 1: New FNOL received
        String newFnolId = "FNOL-001";
        assertFalse(mockDb.exists("Claims", newFnolId));
        orchestrator.evaluateEngagementDecision(newFnolId, "NEW_FNOL");
        assertTrue(mockDb.exists("Claims", newFnolId));
        assertTrue(orchestrator.isDecisionApplied(newFnolId));

        // Scenario 2: Existing claim updated
        String existingClaimId = "CLM-002";
        mockDb.putItem("Claims", existingClaimId, Map.of("status", "OPEN"));
        orchestrator.evaluateEngagementDecision(existingClaimId, "CLAIM_UPDATED");
        assertTrue(orchestrator.isDecisionApplied(existingClaimId));

        // Concurrency & thread safety NFR verification
        Thread t1 = new Thread(() -> orchestrator.evaluateEngagementDecision("TH-001", "NEW_FNOL"));
        Thread t2 = new Thread(() -> orchestrator.evaluateEngagementDecision("TH-002", "CLAIM_UPDATED"));
        t1.start(); t2.start();
        try { t1.join(); t2.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        assertTrue(orchestrator.isDecisionApplied("TH-001"));
        assertTrue(orchestrator.isDecisionApplied("TH-002"));
    }
}
