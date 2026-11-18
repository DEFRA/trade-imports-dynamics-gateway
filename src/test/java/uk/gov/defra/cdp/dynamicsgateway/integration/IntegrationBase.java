package uk.gov.defra.cdp.dynamicsgateway.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    @Autowired
    private TestRestTemplate eventsClient;

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("azure.servicebus.connection-string",
            () -> SERVICE_BUS_CONTAINER.getConnectionString() + "EntityPath=" + QUEUE_NAME);
    }

    ResponseEntity<String> postEvent(String aggregateId) {
        return postEvent(aggregateId, new HttpHeaders());
    }

    ResponseEntity<String> postEvent(String aggregateId, String traceId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-cdp-request-id", traceId);
        return postEvent(aggregateId, headers);
    }

    private ResponseEntity<String> postEvent(String aggregateId, HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        return eventsClient.postForEntity(
            "/events",
            new HttpEntity<>("{\"aggregateId\":\"" + aggregateId + "\"}", headers),
            String.class);
    }
}
