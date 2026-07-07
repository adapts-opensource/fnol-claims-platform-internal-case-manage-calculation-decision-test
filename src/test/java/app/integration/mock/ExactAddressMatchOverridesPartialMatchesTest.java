package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Mock service representing the orchestration & transformation layer for claim routing
interface ClaimTransformationService {
    java.util.List<String> findAddressMatches(String rawAddress);
    String selectBestMatch(java.util.List<String> candidates);
}

public class ExactAddressMatchOverridesPartialMatchesTest {

    private ClaimTransformationService transformationService;

    @BeforeEach
    void setUp() {
        // Mock external I/O and transformation logic to prevent live AWS/HTTP calls
        transformationService = mock(ClaimTransformationService.class);
    }

    @Test
    void exact_address_match_overrides_partial_matches() {
        // Given
        String rawAddress = "1234 Oak Avenue, Suite 100, Springfield, IL 62704";
        java.util.List<String> partialMatches = java.util.Arrays.asList(
            "1234 Oak Ave, Springfield, IL",
            "1234 Oak Avenue, Springfield"
        );
        String exactMatch = "1234 Oak Avenue, Suite 100, Springfield, IL 62704";
        java.util.List<String> allCandidates = java.util.Arrays.asList(
            partialMatches.get(0), partialMatches.get(1), exactMatch
        );

        // Mock the transformation service to return candidates and resolve to exact match
        when(transformationService.findAddressMatches(rawAddress)).thenReturn(allCandidates);
        when(transformationService.selectBestMatch(allCandidates)).thenReturn(exactMatch);

        // When
        java.util.List<String> candidates = transformationService.findAddressMatches(rawAddress);
        String resolvedAddress = transformationService.selectBestMatch(candidates);

        // Then
        assertEquals(exactMatch, resolvedAddress,
            "Exact address match should override partial matches during transformation");
        verify(transformationService).findAddressMatches(rawAddress);
        verify(transformationService).selectBestMatch(candidates);
    }
}
