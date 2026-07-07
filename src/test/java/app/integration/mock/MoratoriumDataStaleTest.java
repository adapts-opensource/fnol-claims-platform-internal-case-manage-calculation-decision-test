package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MoratoriumDataStaleTest {

    @Mock
    private MoratoriumDataSource moratoriumDataSource;

    @Mock
    private DecisionEngine decisionEngine;

    private OrchestrationDecisionService service;

    @BeforeEach
    void setUp() {
        service = new OrchestrationDecisionService(moratoriumDataSource, decisionEngine);
    }

    @Test
    void moratorium_data_stale() {
        // Arrange
        String claimId = "CLM-MOR-001";
        Instant staleTimestamp = Instant.now().minusSeconds(7200);
        MoratoriumData staleData = new MoratoriumData(claimId, staleTimestamp, MoratoriumStatus.ACTIVE);

        when(moratoriumDataSource.fetchForClaim(claimId)).thenReturn(Optional.of(staleData));
        when(decisionEngine.evaluate(any())).thenReturn(DecisionStatus.PENDING_REVALIDATION);

        // Act
        DecisionStatus result = service.processDecision(claimId);

        // Assert
        assertEquals(DecisionStatus.PENDING_REVALIDATION, result);
        verify(moratoriumDataSource).fetchForClaim(claimId);
        verify(decisionEngine).evaluate(any());
    }

    // Supporting types for compilation
    static class MoratoriumData {
        final String claimId;
        final Instant lastUpdated;
        final MoratoriumStatus status;

        MoratoriumData(String claimId, Instant lastUpdated, MoratoriumStatus status) {
            this.claimId = claimId;
            this.lastUpdated = lastUpdated;
            this.status = status;
        }
    }

    enum MoratoriumStatus { ACTIVE, EXPIRED, SUSPENDED }
    enum DecisionStatus { APPROVED, REJECTED, PENDING_REVALIDATION, ERROR }

    interface MoratoriumDataSource {
        Optional<MoratoriumData> fetchForClaim(String claimId);
    }

    interface DecisionEngine {
        DecisionStatus evaluate(MoratoriumData data);
    }

    static class OrchestrationDecisionService {
        private final MoratoriumDataSource dataSource;
        private final DecisionEngine engine;

        OrchestrationDecisionService(MoratoriumDataSource dataSource, DecisionEngine engine) {
            this.dataSource = dataSource;
            this.engine = engine;
        }

        DecisionStatus processDecision(String claimId) {
            Optional<MoratoriumData> data = dataSource.fetchForClaim(claimId);
            return data.map(engine::evaluate).orElse(DecisionStatus.ERROR);
        }
    }
}
