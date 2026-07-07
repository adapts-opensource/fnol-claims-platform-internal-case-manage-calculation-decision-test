package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DeadlineCalendarStaleFlagForUpdateTest {

    @Mock
    private ClaimInitiationRoutingDecisionValidationRepository mockRepository;

    @InjectMocks
    private ClaimDecisionCalculationService calculationService;

    @BeforeEach
    void setUp() {
        // Reset mocks and establish baseline behavior before each test execution
        lenient().when(mockRepository.findById(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void deadline_calendar_stale_flag_for_update() {
        // Given: Payload simulating a claim initiation with a stale deadline calendar
        String claimId = "claim-init-001";
        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("deadlineCalendar", Map.of("lastUpdated", "2022-06-15", "status", "STALE"));
        inputPayload.put("policyType", "AUTO");

        // When: Execute the routing decision calculation service
        Map<String, Object> resultPayload = calculationService.calculateRoutingDecision(claimId, inputPayload);

        // Then: Verify the stale calendar state correctly triggers the update flag
        assertNotNull(resultPayload);
        assertTrue((Boolean) resultPayload.get("flagForUpdate"), "Should flag for update when deadline calendar is stale");
        assertEquals("PENDING_REVIEW", resultPayload.get("routingDestination"));
        verify(mockRepository).findById(claimId);
    }
}
