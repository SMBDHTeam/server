package com.server.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("공통 오류 응답 OpenAPI 문서화")
class ErrorResponseOpenApiConfigTest {

    private final ErrorResponseOpenApiConfig config = new ErrorResponseOpenApiConfig();

    private OpenAPI documentFor(String path, PathItem.HttpMethod method, Operation operation) {
        PathItem pathItem = new PathItem();
        pathItem.operation(method, operation);
        OpenAPI openApi = new OpenAPI().components(new Components());
        openApi.setPaths(new Paths().addPathItem(path, pathItem));
        config.errorResponseCustomizer().customise(openApi);
        return openApi;
    }

    private Operation operationWithSuccess(String status) {
        return new Operation().responses(
                new ApiResponses().addApiResponse(status, new ApiResponse().description("성공")));
    }

    @Test
    @DisplayName("ErrorResponse 스키마를 components에 등록한다")
    void registersErrorSchema() {
        OpenAPI openApi = documentFor(
                "/api/v1/trip-questions", PathItem.HttpMethod.GET, operationWithSuccess("200"));

        assertThat(openApi.getComponents().getSchemas()).containsKeys("ErrorResponse", "FieldErrorResponse");
    }

    @Test
    @DisplayName("모든 오퍼레이션에 500을 붙이고 ErrorResponse를 참조한다")
    void addsInternalErrorEverywhere() {
        OpenAPI openApi = documentFor(
                "/api/v1/trip-questions", PathItem.HttpMethod.GET, operationWithSuccess("200"));

        ApiResponse internal = openApi.getPaths()
                .get("/api/v1/trip-questions").getGet().getResponses().get("500");
        assertThat(internal).isNotNull();
        assertThat(internal.getContent().get("application/json").getSchema().get$ref())
                .isEqualTo("#/components/schemas/ErrorResponse");
        assertThat(internal.getDescription()).contains("INTERNAL_ERROR");
    }

    @Test
    @DisplayName("입력을 받지 않는 오퍼레이션에는 400을 붙이지 않는다")
    void skipsBadRequestWhenOperationTakesNoInput() {
        OpenAPI openApi = documentFor(
                "/api/v1/trip-questions", PathItem.HttpMethod.GET, operationWithSuccess("200"));

        assertThat(openApi.getPaths().get("/api/v1/trip-questions").getGet().getResponses())
                .doesNotContainKey("400");
    }

    @Test
    @DisplayName("경로 변수가 있으면 404를 붙인다")
    void addsNotFoundForPathVariable() {
        Operation operation = operationWithSuccess("200")
                .parameters(List.of(new Parameter().name("placeId").in("path")));
        OpenAPI openApi = documentFor("/api/v1/places/{placeId}", PathItem.HttpMethod.GET, operation);

        ApiResponses responses = openApi.getPaths().get("/api/v1/places/{placeId}").getGet().getResponses();
        assertThat(responses).containsKeys("400", "404", "500");
        assertThat(responses).doesNotContainKeys("409", "410", "503");
    }

    @Test
    @DisplayName("Preview를 소비하는 일정 생성에만 409와 410을 붙인다")
    void addsConflictOnlyWherePreviewIsConsumed() {
        Operation create = operationWithSuccess("201").requestBody(new RequestBody());
        ApiResponses created = documentFor("/api/v1/schedules", PathItem.HttpMethod.POST, create)
                .getPaths().get("/api/v1/schedules").getPost().getResponses();
        assertThat(created).containsKeys("409", "410", "503");
        assertThat(created.get("409").getDescription()).contains("SCHEDULE_CREATION_IN_PROGRESS");

        ApiResponses listed = documentFor(
                "/api/v1/schedules", PathItem.HttpMethod.GET, operationWithSuccess("200"))
                .getPaths().get("/api/v1/schedules").getGet().getResponses();
        assertThat(listed).doesNotContainKeys("409", "410");
        assertThat(listed).containsKey("503");
    }

    @Test
    @DisplayName("공유 링크 발급은 위임을 타지 않으므로 503을 붙이지 않는다")
    void skipsProviderUnavailableForShareLink() {
        Operation operation = operationWithSuccess("201")
                .parameters(List.of(new Parameter().name("scheduleId").in("path")));
        ApiResponses responses = documentFor(
                "/api/v1/schedules/{scheduleId}/shares", PathItem.HttpMethod.POST, operation)
                .getPaths().get("/api/v1/schedules/{scheduleId}/shares").getPost().getResponses();

        assertThat(responses).containsKeys("400", "404", "500");
        assertThat(responses).doesNotContainKey("503");
    }

    @Test
    @DisplayName("공유된 일정 조회는 위임을 타므로 503을 붙인다")
    void addsProviderUnavailableForSharedSchedule() {
        Operation operation = operationWithSuccess("200")
                .parameters(List.of(new Parameter().name("token").in("path")));
        ApiResponses responses = documentFor(
                "/api/v1/shared-schedules/{token}", PathItem.HttpMethod.GET, operation)
                .getPaths().get("/api/v1/shared-schedules/{token}").getGet().getResponses();

        assertThat(responses).containsKey("503");
    }

    @Test
    @DisplayName("이미 선언된 응답은 덮어쓰지 않는다")
    void keepsExistingResponse() {
        Operation operation = operationWithSuccess("200")
                .parameters(List.of(new Parameter().name("placeId").in("path")));
        operation.getResponses().addApiResponse("404", new ApiResponse().description("직접 선언한 설명"));

        ApiResponses responses = documentFor("/api/v1/places/{placeId}", PathItem.HttpMethod.GET, operation)
                .getPaths().get("/api/v1/places/{placeId}").getGet().getResponses();

        assertThat(responses.get("404").getDescription()).isEqualTo("직접 선언한 설명");
    }
}
