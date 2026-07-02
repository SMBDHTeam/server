package com.server.external.tourapi;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class TourApiClient {

    private final RestClient restClient;
    private final TourApiProperties properties;

    public TourApiClient(RestClient tourApiRestClient, TourApiProperties properties) {
        this.restClient = tourApiRestClient;
        this.properties = properties;
    }

    public TourApiPlaceListResponse searchPlaces(
            String areaCode,
            String contentTypeId,
            int pageNo,
            int pageSize
    ) {
        Map<String, Object> root = execute(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/areaBasedList2")
                        .queryParam("serviceKey", properties.serviceKey())
                        .queryParam("MobileOS", properties.mobileOs())
                        .queryParam("MobileApp", properties.mobileApp())
                        .queryParam("_type", "json")
                        .queryParam("areaCode", areaCode)
                        .queryParam("contentTypeId", contentTypeId)
                        .queryParam("pageNo", pageNo)
                        .queryParam("numOfRows", pageSize)
                        .queryParam("arrange", "A")
                        .build())
                .retrieve()
                .body(Map.class));

        Map<String, Object> body = map(map(root, "response"), "body");
        return new TourApiPlaceListResponse(
                intValue(body, "totalCount"),
                items(body).stream()
                        .map(this::toListItem)
                        .toList()
        );
    }

    public TourApiPlaceDetailResponse getCommonDetail(String contentId, String contentTypeId) {
        Map<String, Object> root = execute(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/detailCommon2")
                        .queryParam("serviceKey", properties.serviceKey())
                        .queryParam("MobileOS", properties.mobileOs())
                        .queryParam("MobileApp", properties.mobileApp())
                        .queryParam("_type", "json")
                        .queryParam("contentId", contentId)
                        .queryParam("contentTypeId", contentTypeId)
                        .queryParam("defaultYN", "Y")
                        .queryParam("firstImageYN", "Y")
                        .queryParam("addrinfoYN", "Y")
                        .queryParam("mapinfoYN", "Y")
                        .queryParam("overviewYN", "Y")
                        .build())
                .retrieve()
                .body(Map.class));

        Map<String, Object> item = firstItem(map(map(root, "response"), "body"));
        if (item.isEmpty()) {
            return TourApiPlaceDetailResponse.empty();
        }
        return new TourApiPlaceDetailResponse(
                text(item, "overview"),
                text(item, "homepage"),
                rawJson(item)
        );
    }

    public TourApiPlaceIntroResponse getIntro(String contentId, String contentTypeId) {
        Map<String, Object> root = execute(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/detailIntro2")
                        .queryParam("serviceKey", properties.serviceKey())
                        .queryParam("MobileOS", properties.mobileOs())
                        .queryParam("MobileApp", properties.mobileApp())
                        .queryParam("_type", "json")
                        .queryParam("contentId", contentId)
                        .queryParam("contentTypeId", contentTypeId)
                        .build())
                .retrieve()
                .body(Map.class));

        Map<String, Object> item = firstItem(map(map(root, "response"), "body"));
        if (item.isEmpty()) {
            return TourApiPlaceIntroResponse.empty();
        }
        return new TourApiPlaceIntroResponse(
                firstText(item, "usetime", "opentimefood", "usetimeculture", "usetimeleports", "playtime"),
                firstText(item, "restdate", "restdatefood", "restdateculture", "restdateleports"),
                firstText(item, "usefee", "usetimefestival", "usefeeleports", "chkcreditcardfood"),
                firstText(item, "parking", "parkingfood", "parkingculture", "parkingleports"),
                true,
                rawJson(item)
        );
    }

    public TourApiPlaceImageResponse getImages(String contentId) {
        Map<String, Object> root = execute(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/detailImage2")
                        .queryParam("serviceKey", properties.serviceKey())
                        .queryParam("MobileOS", properties.mobileOs())
                        .queryParam("MobileApp", properties.mobileApp())
                        .queryParam("_type", "json")
                        .queryParam("contentId", contentId)
                        .queryParam("imageYN", "Y")
                        .queryParam("subImageYN", "Y")
                        .build())
                .retrieve()
                .body(Map.class));

        return new TourApiPlaceImageResponse(items(map(map(root, "response"), "body"))
                .stream()
                .map(item -> new TourApiPlaceImageResponse.Item(
                        text(item, "originimgurl"),
                        text(item, "smallimageurl"),
                        text(item, "cpyrhtDivCd")
                ))
                .filter(item -> item.url() != null)
                .toList());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> execute(TourApiRequest request) {
        if (properties.serviceKey().isBlank()) {
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE);
        }
        try {
            Map<String, Object> root = request.get();
            if (root == null || root.isEmpty()) {
                throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE);
            }
            validateResultCode(root);
            return root;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientResponseException | ResourceAccessException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE, exception);
        }
    }

    private void validateResultCode(Map<String, Object> root) {
        Map<String, Object> header = map(map(root, "response"), "header");
        String resultCode = text(header, "resultCode");
        if (resultCode != null && !"0000".equals(resultCode)) {
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE);
        }
    }

    private TourApiPlaceListResponse.Item toListItem(Map<String, Object> item) {
        return new TourApiPlaceListResponse.Item(
                text(item, "contentid"),
                text(item, "contenttypeid"),
                text(item, "title"),
                firstText(item, "cat3", "cat2", "cat1"),
                joinAddress(text(item, "addr1"), text(item, "addr2")),
                text(item, "mapx"),
                text(item, "mapy"),
                text(item, "firstimage"),
                rawJson(item)
        );
    }

    private List<Map<String, Object>> items(Map<String, Object> body) {
        Object itemNode = map(body, "items").get("item");
        if (itemNode == null) {
            return List.of();
        }
        if (itemNode instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add(stringObjectMap(map));
                }
            }
            return result;
        }
        if (itemNode instanceof Map<?, ?> map) {
            return List.of(stringObjectMap(map));
        }
        return List.of();
    }

    private Map<String, Object> firstItem(Map<String, Object> body) {
        List<Map<String, Object>> items = items(body);
        if (items.isEmpty()) {
            return Map.of();
        }
        return items.get(0);
    }

    private String joinAddress(String addr1, String addr2) {
        if (addr1 == null) {
            return addr2;
        }
        if (addr2 == null) {
            return addr1;
        }
        return addr1 + " " + addr2;
    }

    private String firstText(Map<String, Object> node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = text(node, fieldName);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String text(Map<String, Object> node, String fieldName) {
        Object value = node.get(fieldName);
        if (value == null) {
            return null;
        }
        String text = value.toString();
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.trim();
    }

    private int intValue(Map<String, Object> node, String fieldName) {
        Object value = node.get(fieldName);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private Map<String, Object> map(Map<String, Object> node, String fieldName) {
        Object value = node.get(fieldName);
        if (value instanceof Map<?, ?> map) {
            return stringObjectMap(map);
        }
        return Map.of();
    }

    private Map<String, Object> stringObjectMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(key.toString(), value));
        return result;
    }

    private String rawJson(Map<String, Object> node) {
        return jsonValue(node);
    }

    private String jsonValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String text) {
            return "\"" + escapeJson(text) + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map) {
            List<String> entries = new ArrayList<>();
            map.forEach((key, entryValue) ->
                    entries.add("\"" + escapeJson(key.toString()) + "\":" + jsonValue(entryValue)));
            return "{" + String.join(",", entries) + "}";
        }
        if (value instanceof List<?> list) {
            return "[" + String.join(",", list.stream()
                    .map(this::jsonValue)
                    .toList()) + "]";
        }
        return "\"" + escapeJson(value.toString()) + "\"";
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @FunctionalInterface
    private interface TourApiRequest {
        Map<String, Object> get();
    }
}
