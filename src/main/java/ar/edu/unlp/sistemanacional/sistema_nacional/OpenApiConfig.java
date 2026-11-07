package ar.edu.unlp.sistemanacional.sistema_nacional;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.customizers.OpenApiCustomizer;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Sistema Nacional API",
        version = "1.0",
        description = "Flujo recomendado: registra un usuario y obtén un JWT en /api/auth; pulsa Authorize y pega el token. "
                + "Luego crea o consulta la ONG, usa su codigo_ong para asociar recursos y para las integraciones entre sistemas. "
                + "Valida ofertas antes de comprometer recursos para una convocatoria y libera los compromisos cuando corresponda. "
                + "Los endpoints protegidos requieren Authorization: Bearer <accessToken>."))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {

    @Bean
    OpenApiCustomizer internalServerErrorResponse() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }

            openApi.getPaths().values().forEach(pathItem -> pathItem.readOperations().forEach(operation ->
                    operation.getResponses().addApiResponse("500",
                            new ApiResponse().description("Error interno inesperado; la solicitud no pudo completarse."))));
        };
    }
}