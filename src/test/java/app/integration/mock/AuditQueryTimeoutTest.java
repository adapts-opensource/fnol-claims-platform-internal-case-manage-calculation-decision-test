package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
public class AuditQueryTimeoutTest {

    @Mock
    private AuditQueryService auditQueryService;

    private StateTransitionService stateTransitionService;

    @BeforeEach
    void setUp() {
        stateTransitionService = new StateTransitionService(auditQueryService);
    }

    @Test
    void audit_query_timeout() {
        String claimId = "CLM-10293";
        String nextState = "PENDING_REVIEW";

        doThrow(new TimeoutException("Audit query timed out after 30s"))
            .when(auditQueryService)
            .queryAuditLogs(eq(claimId), any(Duration.class));

        assertThrows(TimeoutException.class, () ->
            stateTransitionService.applyStateTransition(claimId, nextState)
        );
    }
}
