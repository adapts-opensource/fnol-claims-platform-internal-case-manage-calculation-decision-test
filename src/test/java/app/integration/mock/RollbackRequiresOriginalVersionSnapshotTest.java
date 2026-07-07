package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RollbackRequiresOriginalVersionSnapshotTest {

    @Mock
    private DecisionEnrichmentRepository decisionEnrichmentRepository;

    private ClaimDecisionEnrichmentService claimDecisionEnrichmentService;

    @BeforeEach
    void setUp() {
        claimDecisionEnrichmentService = new ClaimDecisionEnrichmentService(decisionEnrichmentRepository);
    }

    @Test
    void rollback_requires_original_version_snapshot() {
        // Arrange: Simulate missing original version snapshot
        String claimId = "CLM-98765";
        String originalVersionId = "VER-001";

        when(decisionEnrichmentRepository.findOriginalVersionSnapshot(claimId, originalVersionId))
                .thenReturn(Optional.empty());

        // Act & Assert: Rollback must fail when original snapshot is absent
        EnrichmentValidationException exception = assertThrows(
                EnrichmentValidationException.class,
                () -> claimDecisionEnrichmentService.rollbackDecision(claimId, originalVersionId)
        );

        assertEquals("Original version snapshot is required for rollback.", exception.getMessage());

        // Verify repository was queried correctly
        verify(decisionEnrichmentRepository).findOriginalVersionSnapshot(claimId, originalVersionId);
        verifyNoMoreInteractions(decisionEnrichmentRepository);
    }

    // Minimal stubs for compilation context
    interface DecisionEnrichmentRepository {
        Optional<String> findOriginalVersionSnapshot(String claimId, String versionId);
    }

    static class EnrichmentValidationException extends RuntimeException {
        EnrichmentValidationException(String message) { super(message); }
    }

    static class ClaimDecisionEnrichmentService {
        private final DecisionEnrichmentRepository repository;

        ClaimDecisionEnrichmentService(DecisionEnrichmentRepository repository) {
            this.repository = repository;
        }

        void rollbackDecision(String claimId, String originalVersionId) {
            if (repository.findOriginalVersionSnapshot(claimId, originalVersionId).isEmpty()) {
                throw new EnrichmentValidationException("Original version snapshot is required for rollback.");
            }
            // Proceed with rollback logic...
        }
    }
}
