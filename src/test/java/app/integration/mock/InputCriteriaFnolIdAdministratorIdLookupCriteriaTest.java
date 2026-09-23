package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InputCriteriaFnolIdAdministratorIdLookupCriteriaTest {

    @Mock
    private InsuredEngagementStateTransitionService stateTransitionService;

    private InsuredEngagementProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new InsuredEngagementProcessor(stateTransitionService);
    }

    @Test
    void input_criteria_fnol_id_administrator_id_lookup_criteria() {
        String fnolId = "FNOL-123456";
        String administratorId = "ADMIN-789012";
        Map<String, String> lookupCriteria = new HashMap<>();
        lookupCriteria.put("engagement_status", "ACTIVE");
        lookupCriteria.put("tracking_reference", "TRK-999888");

        doNothing().when(stateTransitionService).processTransition(eq(fnolId), eq(administratorId), anyMap());

        assertDoesNotThrow(() -> processor.handleInputCriteria(fnolId, administratorId, lookupCriteria));

        verify(stateTransitionService, times(1)).processTransition(eq(fnolId), eq(administratorId), anyMap());
        verifyNoMoreInteractions(stateTransitionService);
    }
}

interface InsuredEngagementStateTransitionService {
    void processTransition(String fnolId, String administratorId, Map<String, String> lookupCriteria);
}

class InsuredEngagementProcessor {
    private final InsuredEngagementStateTransitionService stateTransitionService;

    InsuredEngagementProcessor(InsuredEngagementStateTransitionService stateTransitionService) {
        this.stateTransitionService = stateTransitionService;
    }

    void handleInputCriteria(String fnolId, String administratorId, Map<String, String> lookupCriteria) {
        if (fnolId == null || administratorId == null || lookupCriteria == null) {
            throw new IllegalArgumentException("Input criteria cannot be null");
        }
        stateTransitionService.processTransition(fnolId, administratorId, lookupCriteria);
    }
}
