package pl.dybcio.ordered.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  private static final String BEARER_SCHEME = "bearerAuth";

  @Bean
  public OpenAPI orderedOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Ordered API")
                .version("v1")
                .description(
                        "Backend for a distributed e-commerce system (modeled after Allegro) - "
                                + "product catalog, orders, warehouse, pricing, and JWT authorization.")
                .contact(
                    new Contact()
                        .name("Adam Dybcio")
                        .url("https://github.com/AdamDybcio")
                        .email("adam.dybcio.kontakt@gmail.com"))
                .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
        .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER_SCHEME,
                    new SecurityScheme()
                        .name(BEARER_SCHEME)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
  }
}
