package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Supporting types for compilation context
interface DuplicateCheckService { boolean isDuplicate(String caseId); }
record DecisionOutcome(DecisionOutcome.Status status, boolean duplicateCheckPassed) {
    enum Status { APPLIED, REJECTED }
}
class CaseDecisionCalculator {
    private final DuplicateCheckService duplicateCheckService;
    CaseDecisionCalculator(DuplicateCheckService duplicateCheckService) {
        this.duplicateCheckService = duplicateCheckService;
    }
    DecisionOutcome evaluate(String caseId) {
        boolean passed = !duplicateCheckService.isDuplicate(caseId);
        return new DecisionOutcome(passed ? DecisionOutcome.Status.APPLIED : DecisionOutcome.Status.REJECTED, passed);
    }
}

@ExtendWith(MockitoExtension.class)
public class InternalCaseManagementDecisionMockTest {

    @Mock
    private DuplicateCheckService duplicateCheckService;

    @InjectMocks
    private CaseDecisionCalculator caseDecisionCalculator;

    @Test
    void applies_when_duplicate_check_passed() {
        // Arrange: Mock external duplicate check to return passed (isDuplicate = false)
        String caseId = "CASE-INT-001";
        when(duplicateCheckService.isDuplicate(caseId)).thenReturn(false);

        // Act: Trigger internal case management decision calculation
        DecisionOutcome outcome = caseDecisionCalculator.evaluate(caseId);

        // Assert: Verify decision applies when duplicate check passes
        assertNotNull(outcome);
        assertEquals(DecisionOutcome.Status.APPLIED, outcome.status());
        assertTrue(outcome.duplicateCheckPassed());
        verify(duplicateCheckService, times(1)).isDuplicate(caseId);
    }
}
