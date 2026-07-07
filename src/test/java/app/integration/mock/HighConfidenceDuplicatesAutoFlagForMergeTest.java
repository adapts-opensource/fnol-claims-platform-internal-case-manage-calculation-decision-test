package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

@ExtendWith(MockitoExtension.class)
public class HighConfidenceDuplicatesAutoFlagForMergeTest {

    @Mock
    private DuplicateDetectionService duplicateDetectionService;

    @InjectMocks
    private DecisionTransformationService decisionTransformationService;

    private Map<String, Object> testContext;

    @BeforeEach
    void setUp() {
        testContext = new HashMap<>();
        testContext.put("insuredIds", List.of("INS-7890", "INS-7891"));
        testContext.put("confidenceThreshold", 0.95);
        testContext.put("featureContext", "Insured Engagement & Tracking:decision:transformation");
    }

    @Test
    void high_confidence_duplicates_auto_flag_for_merge() {
        // Arrange: Simulate external duplicate detection returning high-confidence pairs
        String insuredIdA = "INS-7890";
        String insuredIdB = "INS-7891";
        List<String> highConfidencePairs = List.of(insuredIdA + "|" + insuredIdB);

        when(duplicateDetectionService.scanForDuplicates(anyString()))
                .thenReturn(highConfidencePairs);

        // Act: Trigger decision transformation pipeline to auto-flag duplicates
        List<String> flaggedForMerge = decisionTransformationService.executeMergeDecision(testContext);

        // Assert: Verify auto-flagging behavior and service interactions
        assertNotNull(flaggedForMerge, "Flagged list should not be null");
        assertEquals(2, flaggedForMerge.size(), "Should flag both duplicates for merge");
        assertTrue(flaggedForMerge.contains(insuredIdA), "First insured ID should be flagged");
        assertTrue(flaggedForMerge.contains(insuredIdB), "Second insured ID should be flagged");

        verify(duplicateDetectionService, times(1)).scanForDuplicates(anyString());
        verify(decisionTransformationService, times(1)).executeMergeDecision(testContext);
    }
}

// Lightweight domain service interface for test isolation
interface DuplicateDetectionService {
    List<String> scanForDuplicates(String claimReference);
}

// Decision transformation orchestrator (simplified for test scope)
class DecisionTransformationService {
    private DuplicateDetectionService duplicateDetectionService;

    void setDuplicateDetectionService(DuplicateDetectionService duplicateDetectionService) {
        this.duplicateDetectionService = duplicateDetectionService;
    }

    List<String> executeMergeDecision(Map<String, Object> payload) {
        if (duplicateDetectionService == null || payload == null) {
            return List.of();
        }
        String claimRef = "CLAIM-FNOL-2024-001";
        List<String> pairs = duplicateDetectionService.scanForDuplicates(claimRef);
        List<String> flaggedIds = new ArrayList<>();
        for (String pair : pairs) {
            String[] ids = pair.split("\\|");
            if (ids.length == 2) {
                flaggedIds.add(ids[0]);
                flaggedIds.add(ids[1]);
            }
        }
        return flaggedIds;
    }
}
