package app.integration.mock;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimEnrichmentDecisionValidationTest {

    @Mock
    private ClaimIdResolver claimIdResolver;
    @Mock
    private PathValidator pathValidator;

    private ClaimEnrichmentDecisionService enrichmentService;

    @BeforeEach
    void setUp() {
        enrichmentService = new ClaimEnrichmentDecisionService(claimIdResolver, pathValidator);
    }

    @Test
    @DisplayName("input_criteria_required_claim_ids_triage_path_manual_override_flag_final_adjudication_path_optional_confidence_score_exception_count_validation_claim_ids_exist_paths_valid_dates_within_analysis_window_freshness_data_must_be_24_hours_stale")
    void inputCriteriaRequiredClaimIdsTriagePathManualOverrideFlagFinalAdjudicationPathOptionalConfidenceScoreExceptionCountValidationClaimIdsExistPathsValidDatesWithinAnalysisWindowFreshnessDataMustBe24HoursStale() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dataTimestamp = now.minusHours(20); // Simulates freshness < 24 hours

        EnrichmentInputCriteria input = new EnrichmentInputCriteria(
                List.of("CLAIM-001", "CLAIM-002"),
                "TRIAGE_PATH",
                Boolean.TRUE,
                "FINAL_ADJUDICATION_PATH",
                Optional.of(0.95),
                Optional.of(2),
                now.minusDays(1),
                now,
                dataTimestamp
        );

        // Mock external I/O for validation rules
        when(claimIdResolver.existsAll(input.getClaimIds())).thenReturn(true);
        when(pathValidator.isValid(input.getTriagePath())).thenReturn(true);
        when(pathValidator.isValid(input.getFinalAdjudicationPath())).thenReturn(true);

        // Act
        EnrichmentValidationResult result = enrichmentService.validateInput(input);

        // Assert
        assertTrue(result.isValid(), "Should pass validation when all required fields are present and mocks return true");
        assertTrue(result.getErrors().isEmpty(), "No validation errors expected");
        verify(claimIdResolver).existsAll(input.getClaimIds());
        verify(pathValidator, times(2)).isValid(anyString());
    }
}

// Supporting Data Transfer Objects
class EnrichmentInputCriteria {
    private final List<String> claimIds;
    private final String triagePath;
    private final Boolean manualOverrideFlag;
    private final String finalAdjudicationPath;
    private final Optional<Double> confidenceScore;
    private final Optional<Integer> exceptionCount;
    private final LocalDateTime analysisWindowStart;
    private final LocalDateTime analysisWindowEnd;
    private final LocalDateTime dataTimestamp;

    public EnrichmentInputCriteria(List<String> claimIds, String triagePath, Boolean manualOverrideFlag,
                                   String finalAdjudicationPath, Optional<Double> confidenceScore,
                                   Optional<Integer> exceptionCount, LocalDateTime analysisWindowStart,
                                   LocalDateTime analysisWindowEnd, LocalDateTime dataTimestamp) {
        this.claimIds = claimIds;
        this.triagePath = triagePath;
        this.manualOverrideFlag = manualOverrideFlag;
        this.finalAdjudicationPath = finalAdjudicationPath;
        this.confidenceScore = confidenceScore;
        this.exceptionCount = exceptionCount;
        this.analysisWindowStart = analysisWindowStart;
        this.analysisWindowEnd = analysisWindowEnd;
        this.dataTimestamp = dataTimestamp;
    }

    public List<String> getClaimIds() { return claimIds; }
    public String getTriagePath() { return triagePath; }
    public Boolean getManualOverrideFlag() { return manualOverrideFlag; }
    public String getFinalAdjudicationPath() { return finalAdjudicationPath; }
    public Optional<Double> getConfidenceScore() { return confidenceScore; }
    public Optional<Integer> getExceptionCount() { return exceptionCount; }
    public LocalDateTime getAnalysisWindowStart() { return analysisWindowStart; }
    public LocalDateTime getAnalysisWindowEnd() { return analysisWindowEnd; }
    public LocalDateTime getDataTimestamp() { return dataTimestamp; }
}

class EnrichmentValidationResult {
    private final boolean valid;
    private final List<String> errors;

    public EnrichmentValidationResult(boolean valid, List<String> errors) {
        this.valid = valid;
        this.errors = errors;
    }

    public boolean isValid() { return valid; }
    public List<String> getErrors() { return errors; }
}

// Mocked External Interfaces
interface ClaimIdResolver {
    boolean existsAll(List<String> claimIds);
}

interface PathValidator {
    boolean isValid(String path);
}

// Service Under Test (Validates input criteria against business rules)
class ClaimEnrichmentDecisionService {
    private final ClaimIdResolver claimIdResolver;
    private final PathValidator pathValidator;
    private static final int MAX_STALE_HOURS = 24;

    public ClaimEnrichmentDecisionService(ClaimIdResolver claimIdResolver, PathValidator pathValidator) {
        this.claimIdResolver = claimIdResolver;
        this.pathValidator = pathValidator;
    }

    public EnrichmentValidationResult validateInput(EnrichmentInputCriteria input) {
        if (input == null || input.getClaimIds() == null || input.getTriagePath() == null ||
            input.getManualOverrideFlag() == null || input.getFinalAdjudicationPath() == null) {
            return new EnrichmentValidationResult(false, List.of("Required fields missing"));
        }

        boolean claimIdsExist = claimIdResolver.existsAll(input.getClaimIds());
        boolean triagePathValid = pathValidator.isValid(input.getTriagePath());
        boolean finalPathValid = pathValidator.isValid(input.getFinalAdjudicationPath());

        long hoursSinceDataUpdate = java.time.Duration.between(input.getDataTimestamp(), java.time.LocalDateTime.now()).toHours();
        boolean isFresh = hoursSinceDataUpdate < MAX_STALE_HOURS;

        boolean datesInWindow = !input.getAnalysisWindowStart().isAfter(input.getAnalysisWindowEnd());

        if (!claimIdsExist || !triagePathValid || !finalPathValid || !isFresh || !datesInWindow) {
            return new EnrichmentValidationResult(false, List.of("Validation failed: claim_ids_exist, paths_valid, dates_within_analysis_window, or freshness_data_must_be_24_hours_stale"));
        }

        return new EnrichmentValidationResult(true, List.of());
    }
}
