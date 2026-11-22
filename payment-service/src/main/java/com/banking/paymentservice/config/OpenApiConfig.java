package com.banking.paymentservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Banking Payment Service API")
                        .version("1.0")
                        .description("Creates Razorpay payment orders and processes payment provider webhook events."))
                .servers(List.of(new Server().url("http://localhost:8080").description("API Gateway")))
                .tags(List.of(new Tag()
                        .name("PaymentController")
                        .description("Create payment orders and process Razorpay webhook notifications securely.")));
    }

    @Bean
    public OpenApiCustomizer paymentOperationCustomizer() {
        return openApi -> openApi.getPaths().forEach((path, item) -> item.readOperations().forEach(operation -> {
            operation.setTags(List.of("PaymentController"));
            operation.setDescription(paymentOperationDescription(path));

            if (path.endsWith("/webhook")) {
                operation.setSummary("Webhook temporarily unavailable — public domain required");
                operation.setDescription("This Razorpay webhook is currently unavailable.");
            }
        }));
    }

    private String paymentOperationDescription(String path) {
        if (path.endsWith("/create-order")) return "Create a Razorpay payment order for a banking payment request.";
        if (path.endsWith("/webhook")) return "Receive and process Razorpay payment status webhook events.";
        return "Payment service operation: " + path;
    }
}
