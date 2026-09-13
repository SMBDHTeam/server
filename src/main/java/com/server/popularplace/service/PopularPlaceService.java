package com.server.popularplace.service;

import com.server.common.support.Paging;
import com.server.popularplace.dto.PopularPlaceListResponse;
import com.server.popularplace.dto.PopularPlaceResponse;
import com.server.post.repository.PostPlaceTagRepository;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 커뮤니티 장소 태그로 "요즘 사람들이 가는 곳"을 만든다.
 *
 * <p>관광 데이터에는 그런 정보가 없다. 적재해 둔 값은 이름·주소·분류뿐이라 무엇이 인기인지
 * 가릴 근거가 없다. 사용자가 붙인 장소 태그가 유일한 단서다.
 */
@Service
public class PopularPlaceService {

    private final PostPlaceTagRepository postPlaceTagRepository;

    /**
     * 며칠 치를 셀지. "지금" 인기 있는 곳을 보여주려는 것이라 기간을 둔다. 기간이 없으면
     * 작년에 반짝 유행한 곳이 계속 위에 남는다.
     */
    private final int days;

    /**
     * 목록에 올리는 데 필요한 최소 인원.
     *
     * <p><b>기본값 2는 신뢰할 만한 기준이 아니라 데이터가 없어서 낮춰 둔 값이다.</b>
     * 혼자 붙인 것만 걸러내는 수준이며, 글이 쌓이면 올린다. 코드가 아니라 설정이라
     * 배포 없이 바꿀 수 있다.
     */
    private final int minAuthors;

    public PopularPlaceService(
            PostPlaceTagRepository postPlaceTagRepository,
            @Value("${app.community.popular-place.days}") int days,
            @Value("${app.community.popular-place.min-authors}") int minAuthors
    ) {
        this.postPlaceTagRepository = postPlaceTagRepository;
        this.days = days;
        this.minAuthors = minAuthors;
    }

    @Transactional(readOnly = true)
    public PopularPlaceListResponse findPopularPlaces(Integer size) {
        return new PopularPlaceListResponse(
                postPlaceTagRepository.findPopularPlaces(
                                LocalDateTime.now().minusDays(days),
                                minAuthors,
                                Paging.of(0, size))
                        .stream()
                        .map(PopularPlaceResponse::from)
                        .toList());
    }
}
