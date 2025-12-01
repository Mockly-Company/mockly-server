package app.mockly.common;

import com.epages.restdocs.apispec.SimpleType;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;

import java.util.List;
import java.util.stream.Stream;

import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

public class ApiResponseDocs {
    public static final List<FieldDescriptor> COMMON_RESPONSE_FIELDS = List.of(
            fieldWithPath("success").description("성공 여부").type(SimpleType.BOOLEAN),
            fieldWithPath("data").description("응답 데이터 (에러 시 null)").type(JsonFieldType.OBJECT).optional(),
            fieldWithPath("error").description("에러 코드 (성공 시 null)").type(SimpleType.STRING).optional(),
            fieldWithPath("message").description("응답 메시지 (성공 시 null)").type(SimpleType.STRING).optional(),
            fieldWithPath("timestamp").description("응답 타임스탬프 (Unix timestamp, ms)").type(SimpleType.NUMBER)
    );

    public static List<FieldDescriptor> withDataFields(List<FieldDescriptor> dataFields) {
        return Stream.concat(
                COMMON_RESPONSE_FIELDS.stream(),
                dataFields.stream().map(dataField -> {
                    FieldDescriptor descriptor = fieldWithPath("data." + dataField.getPath())
                            .description(dataField.getDescription())
                            .type(dataField.getType());
                    return dataField.isOptional() ? descriptor.optional() : descriptor;
                })
        ).toList();
    }

    public static List<FieldDescriptor> errorResponse() {
        return errorResponse("에러 코드");
    }

    public static List<FieldDescriptor> errorResponse(String errorCodeDescription) {
        return List.of(
                fieldWithPath("success").description("성공 여부 (false)").type(SimpleType.BOOLEAN),
                fieldWithPath("data").description("응답 데이터 (에러 시 null)").type(JsonFieldType.NULL),
                fieldWithPath("error").description(errorCodeDescription).type(SimpleType.STRING),
                fieldWithPath("message").description("에러 메시지").type(SimpleType.STRING),
                fieldWithPath("timestamp").description("응답 타임스탬프").type(SimpleType.NUMBER)
        );
    }

    public static List<FieldDescriptor> noContentFields() {
        return List.of(
                fieldWithPath("success").description("성공 여부 (true)").type(SimpleType.BOOLEAN),
                fieldWithPath("data").description("응답 데이터 (없음)").type(JsonFieldType.NULL),
                fieldWithPath("error").description("에러 코드 (없음)").type(SimpleType.STRING).optional(),
                fieldWithPath("message").description("응답 메시지 (없음)").type(SimpleType.STRING).optional(),
                fieldWithPath("timestamp").description("응답 타임스탬프").type(SimpleType.NUMBER)
        );
    }
}
