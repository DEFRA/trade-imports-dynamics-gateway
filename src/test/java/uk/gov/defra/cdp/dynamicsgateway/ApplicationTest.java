package uk.gov.defra.cdp.dynamicsgateway;

import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("integration-test")
class ApplicationTest {

    @MockitoBean
    ServiceBusSenderClient senderClient;

    @Test
    void contextLoads() {
    }
}
