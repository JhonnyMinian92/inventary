package com.ecuapp.inventary.config;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
@Configuration
@OpenAPIDefinition(info=@Info(title="Inventary Backend API",version="v1",
 description="API REST descubierta desde los controladores y DTOs. Errores RFC 9457 con requestId. JWT Bearer HS256 para rutas privadas y X-Integration-Key para integración WhatsApp.",
 contact=@Contact(name="Inventary Backend")))
@SecurityScheme(name="bearerAuth",type=SecuritySchemeType.HTTP,scheme="bearer",bearerFormat="JWT",description="JWT HS256 obtenido desde POST /api/v1/auth/login")
@SecurityScheme(name="integrationKey",type=SecuritySchemeType.APIKEY,in=SecuritySchemeIn.HEADER,paramName="X-Integration-Key",description="Clave de máquina para integración WhatsApp.")
public class OpenApiConfig {
 @Bean OpenApiCustomizer apiContractCustomizer(){return openApi->{
  if(openApi.getPaths()==null)return;
  openApi.getPaths().forEach((path,item)->operations(item).forEach(operation->{applySecurity(path,operation);addCommonResponses(operation);}));
 };}
 private List<Operation> operations(PathItem item){return Arrays.asList(item.getGet(),item.getPost(),item.getPut(),item.getPatch(),item.getDelete(),item.getHead(),item.getOptions(),item.getTrace()).stream().filter(Objects::nonNull).toList();}
 private void applySecurity(String path,Operation operation){
  if(path.startsWith("/api/v1/public/")||path.equals("/api/v1/auth/login"))return;
  if(path.startsWith("/api/v1/integrations/whatsapp/"))operation.setSecurity(List.of(new SecurityRequirement().addList("integrationKey")));
  else if(path.startsWith("/api/v1/"))operation.setSecurity(List.of(new SecurityRequirement().addList("bearerAuth")));
 }
 private void addCommonResponses(Operation operation){
  var responses=operation.getResponses();if(responses==null){responses=new io.swagger.v3.oas.models.responses.ApiResponses();operation.setResponses(responses);}
  responses.putIfAbsent("400",response("Solicitud inválida; formato RFC 9457."));
  responses.putIfAbsent("401",response("Autenticación ausente o no válida."));
  responses.putIfAbsent("403",response("No se dispone del permiso requerido."));
  responses.putIfAbsent("409",response("Conflicto de unicidad, idempotencia o concurrencia."));
  responses.putIfAbsent("422",response("La solicitud incumple una regla de dominio."));
  responses.putIfAbsent("500",response("Error inesperado; consultar requestId. No se exponen detalles internos."));
 }
 private io.swagger.v3.oas.models.responses.ApiResponse response(String description){
  var schema=new io.swagger.v3.oas.models.media.Schema<>().type("object");
  var media=new io.swagger.v3.oas.models.media.MediaType().schema(schema);
  var content=new io.swagger.v3.oas.models.media.Content().addMediaType("application/problem+json",media);
  return new io.swagger.v3.oas.models.responses.ApiResponse().description(description).content(content);
 }
}
