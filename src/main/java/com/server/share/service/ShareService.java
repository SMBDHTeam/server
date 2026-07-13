package com.server.share.service;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.schedule.domain.Schedule;
import com.server.schedule.dto.ScheduleMapResponse;
import com.server.schedule.repository.ScheduleRepository;
import com.server.schedule.service.ScheduleService;
import com.server.share.domain.ShareLink;
import com.server.share.dto.ShareLinkCreateRequest;
import com.server.share.dto.ShareLinkResponse;
import com.server.share.dto.SharedScheduleResponse;
import com.server.share.repository.ShareLinkRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShareService {

    private static final int TOKEN_BYTES = 32;
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final ScheduleRepository scheduleRepository;
    private final ShareLinkRepository shareLinkRepository;
    private final ScheduleService scheduleService;
    private final SecureRandom secureRandom = new SecureRandom();

    public ShareService(
            ScheduleRepository scheduleRepository,
            ShareLinkRepository shareLinkRepository,
            ScheduleService scheduleService
    ) {
        this.scheduleRepository = scheduleRepository;
        this.shareLinkRepository = shareLinkRepository;
        this.scheduleService = scheduleService;
    }

    @Transactional
    public ShareLinkResponse create(UUID scheduleId, ShareLinkCreateRequest request) {
        if (request.expiresInDays() != null
                && (request.expiresInDays() <= 0 || request.expiresInDays() > 365)) {
            throw new BusinessException(ErrorCode.INVALID_SCHEDULE_CONDITION);
        }
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
        String token = newToken();
        LocalDateTime now = now();
        LocalDateTime expiresAt = request.expiresInDays() == null
                ? null
                : now.plusDays(request.expiresInDays());
        ShareLink shareLink = shareLinkRepository.save(new ShareLink(schedule, hash(token), expiresAt));
        return new ShareLinkResponse(
                shareLink.getId(),
                token,
                "/shared-schedules/" + token,
                shareLink.getExpiresAt() == null
                        ? null
                        : shareLink.getExpiresAt().atZone(SERVICE_ZONE).toOffsetDateTime()
        );
    }

    @Transactional(readOnly = true)
    public SharedScheduleResponse getSharedSchedule(String token) {
        ShareLink shareLink = availableLink(token);
        return SharedScheduleResponse.from(scheduleService.get(shareLink.getSchedule().getId()));
    }

    @Transactional(readOnly = true)
    public ScheduleMapResponse getSharedMap(String token, Integer dayNo) {
        ShareLink shareLink = availableLink(token);
        return scheduleService.getMap(shareLink.getSchedule().getId(), dayNo);
    }

    @Transactional
    public void revoke(UUID scheduleId, UUID shareId) {
        ShareLink shareLink = shareLinkRepository.findById(shareId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHARE_LINK_NOT_FOUND));
        LocalDateTime now = now();
        if (!shareLink.getSchedule().getId().equals(scheduleId) || !shareLink.isAvailable(now)) {
            throw new BusinessException(ErrorCode.SHARE_LINK_NOT_FOUND);
        }
        shareLink.revoke(now);
    }

    private ShareLink availableLink(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.SHARE_LINK_NOT_FOUND);
        }
        ShareLink shareLink = shareLinkRepository.findByTokenHash(hash(token))
                .orElseThrow(() -> new BusinessException(ErrorCode.SHARE_LINK_NOT_FOUND));
        if (!shareLink.isAvailable(now())) {
            throw new BusinessException(ErrorCode.SHARE_LINK_NOT_FOUND);
        }
        return shareLink;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(SERVICE_ZONE);
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
