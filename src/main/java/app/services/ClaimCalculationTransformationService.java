package app.services;

import app.utilities.E2eRuntimeContext;
import app.models.ClaimDataStandardizationCalculationTransform;
import app.models.ClaimResult;

public class ClaimCalculationTransformationService {

    public ClaimDataStandardizationCalculationTransform constructRequestPayload(String arg0, String arg1) {
        return new ClaimDataStandardizationCalculationTransform();
    }

    public ClaimResult transform(String arg0) {
        return new ClaimResult();
    }

}
