package edu.bgu.semscanapi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI (Swagger) configuration for SemScan API
 * Provides interactive API documentation
 */
@Configuration
public class OpenApiConfig {

    @Autowired
    private GlobalConfig globalConfig;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SemScan API")
                        .description("Attendance Management System API")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("SemScan Team")
                                .email("support@semscan.com")
                                .url("https://semscan.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url(globalConfig.getServerUrl())
                                .description("Current server (from app_config)"),
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local development")))
                .addSecurityItem(new SecurityRequirement().addList("NoAuth"))
                .components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes("NoAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .description("No authentication required for POC")));
    }
}
