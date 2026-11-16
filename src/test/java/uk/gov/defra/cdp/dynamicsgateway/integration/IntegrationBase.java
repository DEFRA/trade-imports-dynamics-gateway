package uk.gov.defra.cdp.dynamicsgateway.integration;

import com.azure.core.amqp.AmqpRetryOptions;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.azure.messaging.servicebus.ServiceBusSessionReceiverClient;
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.azure.ServiceBusEmulatorContainer;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
public abstract class IntegrationBase {

    private static final Logger log = LoggerFactory.getLogger(IntegrationBase.class);

    static final String QUEUE_NAME = "test-queue";

    private static final Network NETWORK = Network.newNetwork();

    private static final MSSQLServerContainer<?> MSSQL_CONTAINER =
        new MSSQLServerContainer<>(DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-CU14-ubuntu-22.04"))
            .acceptLicense()
            .withNetwork(NETWORK)
            .withNetworkAliases("sqledge")
            .waitingFor(Wait.forListeningPort());

    static final ServiceBusEmulatorContainer SERVICE_BUS_CONTAINER =
        new ServiceBusEmulatorContainer(
            DockerImageName.parse("mcr.microsoft.com/azure-messaging/servicebus-emulator:1.1.2"))
            .acceptLicense()
            .withNetwork(NETWORK)
            .withMsSqlServerContainer(MSSQL_CONTAINER)
            .withConfig(MountableFile.forClasspathResource("servicebus-config.json"));

    static {
        MSSQL_CONTAINER.start();
        SERVICE_BUS_CONTAINER.start();
    }

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("azure.servicebus.connection-string",
            () -> SERVICE_BUS_CONTAINER.getConnectionString() + "EntityPath=" + QUEUE_NAME);
    }

    /**
     * Receives up to {@code maxMessages} from the next available session. The receiver is
     * RECEIVE_AND_DELETE, so anything returned is consumed.
     */
    protected static List<ServiceBusReceivedMessage> receiveFromAsb(int maxMessages, Duration timeout) {
        try (ServiceBusSessionReceiverClient sessionReceiver = new ServiceBusClientBuilder()
                .connectionString(SERVICE_BUS_CONTAINER.getConnectionString())
                .retryOptions(new AmqpRetryOptions().setTryTimeout(timeout).setMaxRetries(0))
                .sessionReceiver()
                .queueName(QUEUE_NAME)
                .receiveMode(ServiceBusReceiveMode.RECEIVE_AND_DELETE)
                .buildClient();
             ServiceBusReceiverClient receiver = sessionReceiver.acceptNextSession()) {
            return receiver.receiveMessages(maxMessages, timeout).stream().toList();
        } catch (Exception e) {
            log.debug("No ASB message received yet: {}", e.getMessage());
            return List.of();
        }
    }

    /** Empties the ASB queue so a test's message counts reflect only its own traffic. */
    protected static void drainAsb() {
        int emptyPolls = 0;
        for (int attempt = 0; attempt < 30; attempt++) {
            if (receiveFromAsb(10, Duration.ofSeconds(1)).isEmpty()) {
                emptyPolls++;
                if (emptyPolls >= 3) {
                    return;
                }
            } else {
                emptyPolls = 0;
            }
        }
        log.warn("ASB queue still returning messages after drain attempts; counts may be unreliable");
    }
}
