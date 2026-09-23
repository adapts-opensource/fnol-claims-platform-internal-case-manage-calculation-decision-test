package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CauseOfLossMappingTest {

    @Mock
    private ReferenceCatalogService referenceCatalogService;

    private CauseOfLossTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new CauseOfLossTransformer(referenceCatalogService);
    }

    @Test
    void cause_of_loss_must_map_to_reference_catalog_123() {
        // Arrange
        String inputCauseOfLoss = "COLLISION";
        CatalogEntry expectedEntry = new CatalogEntry("COLLISION", "Vehicle collision event", "ACTIVE");
        when(referenceCatalogService.lookup(inputCauseOfLoss)).thenReturn(expectedEntry);

        // Act
        CatalogEntry result = transformer.transform(inputCauseOfLoss);

        // Assert
        assertNotNull(result);
        assertEquals(expectedEntry.code(), result.code());
        assertEquals(expectedEntry.description(), result.description());
        assertEquals(expectedEntry.status(), result.status());
        verify(referenceCatalogService, times(1)).lookup(inputCauseOfLoss);
    }

    record CatalogEntry(String code, String description, String status) {}
    interface ReferenceCatalogService { CatalogEntry lookup(String code); }
    static class CauseOfLossTransformer {
        private final ReferenceCatalogService service;
        CauseOfLossTransformer(ReferenceCatalogService service) { this.service = service; }
        CatalogEntry transform(String code) {
            CatalogEntry entry = service.lookup(code);
            if (entry == null) throw new IllegalArgumentException("Cause of loss not found in reference catalog");
            return entry;
        }
    }
}
