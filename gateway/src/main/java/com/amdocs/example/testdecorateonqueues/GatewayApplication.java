package com.amdocs.example.testdecorateonqueues;

import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.tracing.Tracer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.integration.StaticMessageHeaderAccessor;
import org.springframework.integration.config.GlobalChannelInterceptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.function.Supplier;

@SpringBootApplication
public class GatewayApplication {

	public static final Logger log = LoggerFactory.getLogger(GatewayApplication.class);

	private Sinks.Many<Message<String>> sink = Sinks.many().multicast().onBackpressureBuffer();

	public static void main(String[] args) {
		SpringApplication.run(GatewayApplication.class, args);
	}

	// We had to do this prior to Spring Cloud 2023.0.0
	// it does not seem to be required anymore
	@PostConstruct
	public void enableAutomaticContextPropagation() {
		Hooks.enableAutomaticContextPropagation();
	}

	/**
	 * This filter is used to write the traceId of the current span to Kafka.
	 */
	@Bean
	public GlobalFilter kafkaFilter(Tracer tracer) {
		Hooks.enableAutomaticContextPropagation();
		return (exchange, chain) -> {
			String traceId = "failed: tracer's current span was null!";
			if (tracer.currentSpan() != null) {
				traceId = tracer.currentSpan().context().traceId();
			} else {
				log.error("io.micrometer.tracing.Tracer.currentSpan() returned null!");
			}
			Context context = ContextSnapshotFactory.builder().build().captureAll()
					.updateContext(Context.empty());
			Sinks.EmitResult result = sink.tryEmitNext(MessageBuilder.withPayload("expected traceId: " + traceId)
					.setHeader("test-header", context).build());
			if (result.isFailure()) {
				log.error("Failed to write message to Kafka: " + result);
			} else {
				log.info("Message to kafka successfully sent: " + result);
			}
            return chain.filter(exchange);

        };
	}

	/**
	 * This filter is used to log the traceId of the current span.
	 * It returns status code 599 if the tracer's current span is null.
	 */
	@Bean
	public GlobalFilter loggingFilter(Tracer tracer) {
		return (exchange, chain) -> {
			if (tracer.currentSpan() != null) {
				log.info("traceId: {}", tracer.currentSpan().context().traceId());
			} else {
				log.error("io.micrometer.tracing.Tracer.currentSpan() returned null!");
				exchange.getResponse().setRawStatusCode(599);
				return exchange.getResponse().setComplete();
			}
			return chain.filter(exchange);
		};
	}

	@Bean
	public Supplier<Flux<Message<String>>> test() {
		return () -> this.sink.asFlux();
	}

	@Bean
	@GlobalChannelInterceptor(patterns = "test-out-0")
	public ChannelInterceptor outputChannelInterceptor() {
		return new ChannelInterceptor() {
			@Override
			public Message<?> preSend(Message<?> message, MessageChannel channel) {
//				final ContextView reactorContext = StaticMessageHeaderAccessor.getReactorContext(message);
				ContextView reactorContext  = message.getHeaders().get("test-header", ContextView.class);
				ContextSnapshotFactory.builder().build().setThreadLocalsFrom(reactorContext);
				return message;
			}
		};
	}




}
