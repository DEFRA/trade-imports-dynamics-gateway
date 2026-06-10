package uk.gov.defra.cdp.dynamicsgateway.integration;

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

    static final String QUEUE_NAME = "test-queue";

    private static final Network NETWORK = Network.newNetwork();

    private static final MSSQLServerContainer<?> MSSQL_CONTAINER =
        new MSSQLServerContainer<>(DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-latest"))
            .acceptLicense()
            .withNetwork(NETWORK)
            .withNetworkAliases("sqledge")
            .waitingFor(Wait.forListeningPort());

    static final ServiceBusEmulatorContainer SERVICE_BUS_CONTAINER =
        new ServiceBusEmulatorContainer(
            DockerImageName.parse("mcr.microsoft.com/azure-messaging/servicebus-emulator:latest"))
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
        registry.add("azure.servicebus.connection-string", SERVICE_BUS_CONTAINER::getConnectionString);
        registry.add("azure.servicebus.queue", () -> QUEUE_NAME);
    }
}
