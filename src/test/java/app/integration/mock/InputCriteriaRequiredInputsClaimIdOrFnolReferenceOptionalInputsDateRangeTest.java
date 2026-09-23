package app.integration.mock;

   import org.junit.jupiter.api.Test;
   import org.junit.jupiter.api.BeforeEach;
   import org.junit.jupiter.api.DisplayName;
   import org.junit.jupiter.params.ParameterizedTest;
   import org.junit.jupiter.params.provider.ValueSource;
   import static org.junit.jupiter.api.Assertions.*;
   import static org.mockito.Mockito.*;

   import java.time.LocalDate;
   import java.time.temporal.ChronoUnit;
   import java.util.*;

   // Mock service to simulate the validation logic
   class DecisionValidationService {
       private final Map<String, Map<String, Object>> auditStore;

       public DecisionValidationService(Map<String, Map<String, Object>> auditStore) {
           this.auditStore = auditStore;
       }

       public Map<String, Object> validateAndGenerateReport(String claimId, String fnolRef, LocalDate startDate, LocalDate endDate) {
           // Simplified logic for testing
           if ((claimId == null || claimId.isEmpty()) && (fnolRef == null || fnolRef.isEmpty())) {
               throw new IllegalArgumentException("claim_id or fnol_reference is required");
           }
           String ref = claimId != null ? claimId : fnolRef;
           if (!auditStore.containsKey(ref)) {
               throw new IllegalStateException("Audit record not found for reference: " + ref);
           }
           // Generate mock report
           Map<String, Object> report = new HashMap<>();
           report.put("report_id", UUID.randomUUID().toString());
           report.put("integrity_hash", "mock_hash_" + ref);
           report.put("rule_ids", Collections.singletonList("RULE_001"));
           report.put("inputs", Collections.singletonMap("claim_id", ref));
           report.put("outputs", Collections.singletonMap("decision", "APPROVED"));
           report.put("confidence_scores", Collections.singletonMap("RULE_001", 0.95));
           report.put("generated_at", LocalDate.now());
           return report;
       }
   }
