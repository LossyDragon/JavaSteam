package in.dragonbra.javasteam.util.log;

import in.dragonbra.javasteam.TestBase;
import org.junit.jupiter.api.Test;

// This isn't really a test, but ensures the logger logs and/or throws in the correct format.
public class LoggerTest extends TestBase {

    private static final Logger logger = LogManager.getLogger(LoggerTest.class);

    @Test
    public void logDebug() {
        logger.debug("TEST DEBUG MESSAGE");
    }

    @Test
    public void logDebugAndThrowable() {
        var error = new Exception("SOME DEBUG THROWABLE");
        logger.debug("TEST DEBUG MESSAGE WITH THROWABLE", error);
    }

    @Test
    public void logDebugNoMessageThrowable() {
        var error = new Exception("SOME DEBUG THROWABLE NO MESSAGE");
        logger.debug(error);
    }

    @Test
    public void logError() {
        logger.error("TEST ERROR MESSAGE");
    }

    @Test
    public void logErrorAndThrowable() {
        var error = new Exception("SOME ERROR THROWABLE");
        logger.error("TEST ERROR MESSAGE", error);
    }

    @Test
    public void logErrorNoMessageThrowable() {
        var error = new Exception("SOME ERROR THROWABLE NO MESSAGE");
        logger.error(error);
    }
}
