package com.server.admin.service;

import com.server.admin.domain.AdminAction;
import com.server.admin.domain.AdminActionTargetType;
import com.server.admin.dto.AdminActionListResponse;
import com.server.admin.dto.AdminActionResponse;
import com.server.admin.repository.AdminActionRepository;
import com.server.user.domain.User;
import com.server.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 조치 이력 조회.
 *
 * <p>기록과 조회를 나눈 것은 의도다. {@link AdminActionRecorder} 는 쓰기만 하고
 * 조치 트랜잭션 안에서 돌아야 하며, 이쪽은 읽기 전용이다.
 */
@Service
public class AdminActionQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final AdminActionRepository adminActionRepository;
    private final UserRepository userRepository;

    public AdminActionQueryService(
            AdminActionRepository adminActionRepository, UserRepository userRepository) {
        this.adminActionRepository = adminActionRepository;
        this.userRepository = userRepository;
    }

    /**
     * 최근 조치부터 준다.
     *
     * <p>{@code targetType} 과 {@code targetId} 를 함께 주면 그 대상에 대한 이력만 본다.
     * &ldquo;이 사용자에게 무슨 조치가 있었나&rdquo;가 분쟁 대응의 주 경로다.
     */
    @Transactional(readOnly = true)
    public AdminActionListResponse getActions(
            AdminActionTargetType targetType, Long targetId, Integer page, Integer size) {
        int resolvedPage = page == null || page < 0 ? 0 : page;
        int resolvedSize = size == null || size <= 0
                ? DEFAULT_PAGE_SIZE
                : Math.min(size, MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(resolvedPage, resolvedSize);

        // targetId 만 주면 어느 종류의 5번인지 알 수 없어 무시한다. targetType 만 주는 것은
        // "장소 조치 전부"처럼 쓸모가 있어 그대로 거른다. 부분 조건을 조용히 버리면
        // 거른 줄 알았는데 전체가 나온다.
        if (targetType != null && targetId != null) {
            return new AdminActionListResponse(
                    toResponses(adminActionRepository
                            .findByTargetTypeAndTargetIdOrderByCreatedAtDescIdDesc(
                                    targetType, targetId, pageable)),
                    adminActionRepository.countByTargetTypeAndTargetId(targetType, targetId));
        }
        if (targetType != null) {
            return new AdminActionListResponse(
                    toResponses(adminActionRepository
                            .findByTargetTypeOrderByCreatedAtDescIdDesc(targetType, pageable)),
                    adminActionRepository.countByTargetType(targetType));
        }
        return new AdminActionListResponse(
                toResponses(adminActionRepository.findAllByOrderByCreatedAtDescIdDesc(pageable)),
                adminActionRepository.count());
    }

    /** 관리자 닉네임을 한 번에 읽는다. 행마다 조회하면 페이지당 질의가 스무 번 늘어난다. */
    private List<AdminActionResponse> toResponses(List<AdminAction> actions) {
        List<Long> adminIds = actions.stream().map(AdminAction::getAdminId).distinct().toList();
        Map<Long, User> admins = userRepository.findAllById(adminIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return actions.stream()
                .map(action -> {
                    User admin = admins.get(action.getAdminId());
                    // 조치한 관리자가 탈퇴해도 이력은 남는다. 이름을 못 찾아도 ID 는 보여 준다.
                    return AdminActionResponse.of(action, new AdminActionResponse.Actor(
                            action.getAdminId(),
                            admin == null ? null : admin.getNickname()));
                })
                .toList();
    }
}
