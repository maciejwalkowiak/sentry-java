package net.kencochrane.raven.log4j;

import mockit.Expectations;
import mockit.Injectable;
import mockit.Mocked;
import mockit.Verifications;
import net.kencochrane.raven.Raven;
import net.kencochrane.raven.RavenFactory;
import net.kencochrane.raven.dsn.Dsn;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SentryAppenderDsnTest {
    private SentryAppender sentryAppender;
    private MockUpErrorHandler mockUpErrorHandler;
    @Injectable
    private Raven mockRaven = null;
    @Injectable
    private Logger mockLogger = null;
    @Injectable
    private RavenFactory ravenFactory = null;

    @BeforeMethod
    public void setUp() {
        sentryAppender = new SentryAppender();
        mockUpErrorHandler = new MockUpErrorHandler();
        sentryAppender.setErrorHandler(mockUpErrorHandler.getMockInstance());
        RavenFactory.registerFactory(ravenFactory);
        sentryAppender.setRavenFactory(ravenFactory.getClass().getName());
    }

    @Test
    public void testDsnDetected() throws Exception {
        final String dsnUri = "protocol://public:private@host/1";
        sentryAppender.setRavenFactory(ravenFactory.getClass().getName());
        new Expectations() {
            @Mocked("dsnLookup")
            private Dsn dsn;

            {
                Dsn.dsnLookup();
                result = dsnUri;
                ravenFactory.createRavenInstance(withEqual(new Dsn(dsnUri)));
                result = mockRaven;
            }
        };

        sentryAppender.activateOptions();
        sentryAppender.append(new LoggingEvent(null, mockLogger, 0, Level.ERROR, null, null));

        new Verifications() {{
            assertThat(mockUpErrorHandler.getErrorCount(), is(0));
        }};
    }

    @Test
    public void testDsnProvided() throws Exception {
        final String dsnUri = "protocol://public:private@host/2";
        sentryAppender.setDsn(dsnUri);
        new Expectations() {{
            ravenFactory.createRavenInstance(withEqual(new Dsn(dsnUri)));
            result = mockRaven;
        }};

        sentryAppender.activateOptions();
        sentryAppender.append(new LoggingEvent(null, mockLogger, 0, Level.ERROR, null, null));

        new Verifications() {{
            assertThat(mockUpErrorHandler.getErrorCount(), is(0));
        }};
    }
}
