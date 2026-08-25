package com.server.report.repository;

import com.server.report.domain.Report;
import com.server.report.domain.ReportTargetType;
import com.server.post.domain.ReportStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByReporterIdAndTargetTypeAndTargetId(
            Long reporterId, ReportTargetType targetType, Long targetId);

    /**
     * 관리자 신고 목록. 상태·대상 유형은 없으면 거르지 않는다.
     *
     * <p>대기 중인 것부터 오래된 순으로 본다. 최신순으로 두면 오래 방치된 신고가 계속
     * 뒤로 밀린다.
     */
    @Query("""
            select report from Report report
            join fetch report.reporter
            left join fetch report.handledBy
            where (:status is null or report.status = :status)
              and (:targetType is null or report.targetType = :targetType)
            order by report.createdAt asc
            """)
    List<Report> findForAdmin(
            @Param("status") ReportStatus status,
            @Param("targetType") ReportTargetType targetType,
            Pageable pageable);

    @Query("""
            select count(report) from Report report
            where (:status is null or report.status = :status)
              and (:targetType is null or report.targetType = :targetType)
            """)
    long countForAdmin(
            @Param("status") ReportStatus status,
            @Param("targetType") ReportTargetType targetType);

    long countByReporterId(Long reporterId);

    /**
     * 이 사용자를 대상으로 접수된 신고 수.
     *
     * <p>사용자 직접 신고뿐 아니라 그가 쓴 게시물·댓글에 대한 신고도 함께 센다.
     * 사용자 신고만 세면 문제 글을 반복해 올리는 계정이 깨끗해 보인다.
     */
    @Query("""
            select count(report) from Report report
            where (report.targetType = com.server.report.domain.ReportTargetType.USER
                   and report.targetId = :userId)
               or (report.targetType = com.server.report.domain.ReportTargetType.POST
                   and report.targetId in (select post.id from Post post where post.user.id = :userId))
               or (report.targetType = com.server.report.domain.ReportTargetType.COMMENT
                   and report.targetId in (
                       select comment.id from Comment comment where comment.user.id = :userId))
            """)
    long countAgainstUser(@Param("userId") Long userId);
}
