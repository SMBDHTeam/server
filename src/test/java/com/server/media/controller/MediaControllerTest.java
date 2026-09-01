package com.server.media.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.server.auth.service.AuthenticatedUser;
import com.server.common.error.GlobalExceptionHandler;
import com.server.common.web.TraceIdFilter;
import com.server.media.dto.MediaUploadListResponse;
import com.server.media.dto.MediaUploadResponse;
import com.server.media.service.MediaService;
import com.server.post.domain.MediaType;
import com.server.user.domain.UserRole;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("미디어 업로드 API")
class MediaControllerTest {

    private final MediaService mediaService = Mockito.mock(MediaService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new MediaController(mediaService))
            .setControllerAdvice(new GlobalExceptionHandler())
            // 컨트롤러가 @AuthenticationPrincipal 로 사용자를 받는다. standaloneSetup 은
            // Security 설정을 타지 않으므로 이 해석기를 직접 넣어야 주입된다.
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .addFilters(new TraceIdFilter())
            .build();

    @AfterEach
    void clearLogin() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("올린 파일의 URL 과 종류를 201 로 준다")
    void uploads() throws Exception {
        login(1L);
        when(mediaService.upload(eq(1L), any())).thenReturn(new MediaUploadListResponse(
                List.of(new MediaUploadResponse("https://bucket.s3.ap-northeast-2.amazonaws.com/posts/a.png",
                        MediaType.IMAGE))));

        mockMvc.perform(multipart("/api/v1/media")
                        .file(new MockMultipartFile("files", "a.png", "image/png", new byte[] {1})))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mediaList[0].mediaType").value("IMAGE"));
    }

    @Test
    @DisplayName("로그인하지 않으면 401 을 준다")
    void requiresLogin() throws Exception {
        mockMvc.perform(multipart("/api/v1/media")
                        .file(new MockMultipartFile("files", "a.png", "image/png", new byte[] {1})))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("files 파트가 없으면 미디어 오류 코드로 400 을 준다")
    void missingPart() throws Exception {
        login(1L);

        // 경로별 분기가 없으면 "일정 조건이 올바르지 않습니다"가 나가 프론트가 원인을 못 찾는다.
        mockMvc.perform(multipart("/api/v1/media")
                        .file(new MockMultipartFile("wrong", "a.png", "image/png", new byte[] {1})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MEDIA_FILE"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("files"));
    }

    /** 해석기가 SecurityContextHolder 를 보므로 요청의 principal 이 아니라 여기에 심는다. */
    private static void login(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser(userId, UserRole.USER), null, List.of()));
    }
}
