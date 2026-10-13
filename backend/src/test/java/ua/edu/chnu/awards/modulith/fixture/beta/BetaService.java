package ua.edu.chnu.awards.modulith.fixture.beta;

import ua.edu.chnu.awards.modulith.fixture.alpha.repository.AlphaRepository;

/**
 * Fixture module {@code beta}, reading another module's repository on purpose.
 */
public class BetaService {

    private final AlphaRepository alpha = new AlphaRepository();

    /**
     * A value read through the other module's repository.
     *
     * @return the value
     */
    public String read() {
        return alpha.find();
    }
}
