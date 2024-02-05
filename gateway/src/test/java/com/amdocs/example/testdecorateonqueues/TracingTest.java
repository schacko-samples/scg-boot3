package com.amdocs.example.testdecorateonqueues;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.gateway.filter.NettyRoutingFilter;
import org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@Import(TestChannelBinderConfiguration.class)
@AutoConfigureObservability
class TracingTest {

	private static final String TRACE_ID = "1231231231231231";

	private ListAppender<ILoggingEvent> appender;

	@Autowired
	private TestRestTemplate rest;

	@Autowired
	private OutputDestination output;

	@BeforeEach
	public void init() {
		rest.getRestTemplate().setInterceptors(
				Collections.singletonList((request, body, execution) -> {
					request.getHeaders()
							.add("x-b3-traceid", TRACE_ID);
					request.getHeaders()
							.add("x-b3-spanid", TRACE_ID);
					return execution.execute(request, body);
				}));
		appender = new ListAppender<>();
		appender.start();
		((Logger) LoggerFactory.getLogger(GatewayApplication.class)).addAppender(appender);
		((Logger) LoggerFactory.getLogger(RoutePredicateHandlerMapping.class)).addAppender(appender);
		((Logger) LoggerFactory.getLogger(NettyRoutingFilter.class)).addAppender(appender);
	}

	/**
	 * This test is to ensure that the traceId and spanId are logged.
	 */
	@Test
	void logging() {
		ResponseEntity response = this.rest.getForEntity("http://localhost:8553/headers", String.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		System.out.println(response.getBody());
		assertTrue(appender.list.size() > 0, "No logs to process");
		appender.list.stream().forEach(le -> {
			Map<String, String> mdc = le.getMDCPropertyMap();
			assertTrue(mdc.containsKey("traceId"), "TraceId does not exist for record: " + le.getMDCPropertyMap() + " " + le);
			assertEquals(TRACE_ID, mdc.get("traceId"), "TraceId did not match");
			assertTrue(mdc.containsKey("spanId"), "SpanId does not exist for record: " + le);
		});
	}

	/**
	 * This test verifies that we are able to obtain the traceId and spanId.
	 */
	@Test
	void tracer() {
		ResponseEntity response = this.rest.getForEntity("http://localhost:8553/headers", String.class);
		assertEquals(HttpStatus.OK, response.getStatusCode(), "io.micrometer.tracing.Tracer.currentSpan() was probably null");
	}

	/**
	 * This test verifies that tracing headers are set on kafka messages.
	 * The problem we saws was that traceId and spanIds were set however traceId was different from was expected.
	 * In case of this JUnit tracing headers does not even exist.
	 */
	@Test
	void kafka() {
		ResponseEntity response = this.rest.getForEntity("http://localhost:8553/headers", String.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		Message message = output.receive(5000, "test-topic.destination");
		assertNotNull(message, "Message was null");
		assertTrue(message.getHeaders().containsKey("X-B3-TraceId"), "X-B3-TraceId header does not exist: " + message.getHeaders().keySet());
		assertEquals(TRACE_ID, message.getHeaders().get("X-B3-TraceId"), "X-B3-TraceId header did not match");
	}

}
