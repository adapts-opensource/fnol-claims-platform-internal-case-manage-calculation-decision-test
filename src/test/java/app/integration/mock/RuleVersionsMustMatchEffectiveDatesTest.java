package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuleVersionsMustMatchEffectiveDatesTest {

    @Mock
    private RuleVersionService ruleVersionService;

    @InjectMocks
    private FnoLValidationService fnoLValidationService;

    @BeforeEach
    void setUp() {
        // Ensure thread safety and idempotent test execution by resetting mock stubs
        lenient().when(ruleVersionService.getRuleVersion(anyString(), any())).thenReturn(Optional.empty());
    }

    @Test
    void rule_versions_must_match_effective_dates() {
        // Arrange
        String ruleId = "FNOL_DECISION_RULE_001";
        LocalDate effectiveDate = LocalDate.of(2024, 1, 1);
        String version = "v2.1.0";

        RuleVersion ruleVersion = new RuleVersion(ruleId, version, effectiveDate);
        when(ruleVersionService.getRuleVersion(ruleId, effectiveDate)).thenReturn(Optional.of(ruleVersion));

        // Act
        boolean isValid = fnoLValidationService.validateRuleVersion(ruleId, effectiveDate);

        // Assert
        assertTrue(isValid, "Rule version must match effective dates for valid FNOL submission");
        verify(ruleVersionService, times(1)).getRuleVersion(ruleId, effectiveDate);
    }
}

record RuleVersion(String ruleId, String version, LocalDate effectiveDate) {}

interface RuleVersionService {
    Optional<RuleVersion> getRuleVersion(String ruleId, LocalDate effectiveDate);
}

class FnoLValidationService {
    private final RuleVersionService ruleVersionService;

    FnoLValidationService(RuleVersionService ruleVersionService) {
        this.ruleVersionService = ruleVersionService;
    }

    boolean validateRuleVersion(String ruleId, LocalDate effectiveDate) {
        return ruleVersionService.getRuleVersion(ruleId, effectiveDate)
                .map(rv -> rv.ruleId().equals(ruleId) && rv.effectiveDate().equals(effectiveDate))
                .orElse(false);
    }
}
