package ua.edu.chnu.awards.analytics.service;

import ua.edu.chnu.awards.common.web.NotFoundException;

/**
 * The unit is unknown, not a faculty or department, or not analysable for the caller; one answer for all three,
 * so ids cannot be probed.
 */
public class UnitNotFoundException extends NotFoundException {

    private static final long serialVersionUID = 1L;

    /**
     * Names the unit.
     *
     * @param unitId the requested unit
     */
    public UnitNotFoundException(long unitId) {
        super("Unit " + unitId + " is not available");
    }
}
