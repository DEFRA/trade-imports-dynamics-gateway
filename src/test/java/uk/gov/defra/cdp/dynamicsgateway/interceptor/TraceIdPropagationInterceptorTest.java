package uk.gov.defra.cdp.dynamicsgateway.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

@ExtendWith(MockitoExtension.class)
class TraceIdPropagationInterceptorTest {

    private static final String TRACE_ID_HEADER = "x-cdp-request-id";
    private static final String MDC_TRACE_ID = "trace.id";

    @Mock private HttpRequest request;
    @Mock private ClientHttpRequestExecution execution;
    @Mock private ClientHttpResponse response;

    @Captor private ArgumentCaptor<HttpRequest> requestCaptor;
    @Captor private ArgumentCaptor<byte[]> bodyCaptor;

    private TraceIdPropagationInterceptor interceptor;
    private HttpHeaders headers;

    @BeforeEach
    void setUp() throws IOException {
        interceptor = new TraceIdPropagationInterceptor("x-cdp-request-id");
        headers = new HttpHeaders();
        lenient().when(request.getHeaders()).thenReturn(headers);
        lenient().when(execution.execute(any(HttpRequest.class), any(byte[].class))).thenReturn(response);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void intercept_shouldAddTraceIdHeader_whenMdcContainsTraceId() throws IOException {
        String expectedTraceId = "test-trace-id-12345";
        MDC.put(MDC_TRACE_ID, expectedTraceId);
        byte[] body = new byte[0];

        ClientHttpResponse actualResponse = interceptor.intercept(request, body, execution);

        assertThat(headers.get(TRACE_ID_HEADER)).isNotNull().containsExactly(expectedTraceId);
        verify(execution).execute(request, body);
        assertThat(actualResponse).isSameAs(response);
    }

    @Test
    void intercept_shouldNotAddHeader_whenMdcTraceIdIsNull() throws IOException {
        MDC.remove(MDC_TRACE_ID);
        byte[] body = new byte[0];

        ClientHttpResponse actualResponse = interceptor.intercept(request, body, execution);

        assertThat(headers.get(TRACE_ID_HEADER)).isNull();
        verify(execution).execute(request, body);
        assertThat(actualResponse).isSameAs(response);
    }

    @Test
    void intercept_shouldNotAddHeader_whenMdcTraceIdIsBlank() throws IOException {
        MDC.put(MDC_TRACE_ID, "   ");
        byte[] body = new byte[0];

        ClientHttpResponse actualResponse = interceptor.intercept(request, body, execution);

        assertThat(headers.get(TRACE_ID_HEADER)).isNull();
        verify(execution).execute(request, body);
        assertThat(actualResponse).isSameAs(response);
    }

    @Test
    void intercept_shouldNotAddHeader_whenMdcTraceIdIsEmpty() throws IOException {
        MDC.put(MDC_TRACE_ID, "");
        byte[] body = new byte[0];

        ClientHttpResponse actualResponse = interceptor.intercept(request, body, execution);

        assertThat(headers.get(TRACE_ID_HEADER)).isNull();
        verify(execution).execute(request, body);
        assertThat(actualResponse).isSameAs(response);
    }

    @Test
    void intercept_shouldCallExecution_withCorrectArguments() throws IOException {
        MDC.put(MDC_TRACE_ID, "trace-123");
        byte[] body = "request-body".getBytes();

        interceptor.intercept(request, body, execution);

        verify(execution).execute(requestCaptor.capture(), bodyCaptor.capture());
        assertThat(requestCaptor.getValue()).isSameAs(request);
        assertThat(bodyCaptor.getValue()).isSameAs(body);
    }

    @Test
    void intercept_shouldOverwriteExistingHeader_whenTraceIdExists() throws IOException {
        headers.set(TRACE_ID_HEADER, "old-trace-id");
        String newTraceId = "new-trace-id-67890";
        MDC.put(MDC_TRACE_ID, newTraceId);
        byte[] body = new byte[0];

        interceptor.intercept(request, body, execution);

        assertThat(headers.get(TRACE_ID_HEADER)).containsExactly(newTraceId);
    }
}
