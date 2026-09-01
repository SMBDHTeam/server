package com.server.hashtag.service;

import com.server.hashtag.domain.Hashtag;
import com.server.hashtag.dto.HashtagPlaceListResponse;
import com.server.hashtag.dto.HashtagPlaceResponse;
import com.server.hashtag.dto.HashtagSuggestionListResponse;
import com.server.hashtag.dto.HashtagSuggestionResponse;
import com.server.hashtag.repository.HashtagRepository;
import com.server.hashtag.repository.PostHashtagRepository;
import com.server.post.domain.Post;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HashtagService {

    private static final int DEFAULT_SUGGESTION_SIZE = 10;
    private static final int MAX_SUGGESTION_SIZE = 30;
    private static final int DEFAULT_PLACE_SIZE = 20;
    private static final int MAX_PLACE_SIZE = 50;


    private final HashtagRepository hashtagRepository;
    private final PostHashtagRepository postHashtagRepository;
    /**
     * 장소 목록에 올리는 데 필요한 최소 인원. 한 사람이 여러 번 태그해도 순위가 오르지
     * 않게 하고, 혼자 잘못 붙인 태그가 목록에 뜨지 않게 한다.
     *
     * <p><b>기본값 3은 신뢰할 만한 기준이 아니라 데이터가 없어서 낮춰 둔 값이다.</b>
     * 한 장소에 열 명이 같은 태그를 달려면 그 태그 아래 글이 수백 개는 쌓여야 하는데,
     * 지금은 게시물이 거의 없어 그 기준으로는 아무 장소도 뜨지 않는다. 글이 쌓이면
     * {@code COMMUNITY_HASHTAG_PLACE_MIN_AUTHORS} 를 올린다. 코드가 아니라 설정이라
     * 배포 없이 바꿀 수 있다.
     */
    private final int minAuthorsForPlace;

    public HashtagService(
            HashtagRepository hashtagRepository,
            PostHashtagRepository postHashtagRepository,
            @Value("${app.community.hashtag-place.min-authors}") int minAuthorsForPlace
    ) {
        this.hashtagRepository = hashtagRepository;
        this.postHashtagRepository = postHashtagRepository;
        this.minAuthorsForPlace = minAuthorsForPlace;
    }

    /**
     * 고른 카테고리를 게시물에 연결하고 사용 수를 올린다.
     *
     * <p>본문을 파싱하지 않는다. 사용자가 목록에서 고른 것만 붙으므로, 본문에 {@code #맛집}
     * 을 써도 카테고리가 되지 않는다. 두 경로를 함께 두면 같은 글이 어떤 화면에서는 잡히고
     * 어떤 화면에서는 빠지는 일이 생긴다.
     *
     * <p>등록되지 않은 이름은 무시한다. 여기서 요청을 거절하면 목록에 없는 값 하나 때문에
     * 글 작성 자체가 막히는데, 카테고리는 분류를 돕는 값이지 글의 필수 조건이 아니다.
     *
     * @return 실제로 연결한 카테고리 이름. 무시한 것은 담기지 않는다.
     */
    @Transactional
    public List<String> attach(Post post, List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        List<String> normalized = requested.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name.trim().toLowerCase())
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            return List.of();
        }

        // 등록되지 않은 이름은 여기서 걸러진다.
        Map<String, Hashtag> registered = hashtagRepository.findByNameIn(normalized).stream()
                .collect(Collectors.toMap(Hashtag::getName, Function.identity()));
        List<String> names = normalized.stream().filter(registered::containsKey).toList();
        if (names.isEmpty()) {
            return List.of();
        }

        // 실제로 새로 연결된 것만 사용 수를 올린다. 같은 게시물을 동시에 두 번 저장해도
        // 한쪽만 연결되고, 이미 연결돼 있으면 사용 수가 두 번 오르지 않는다.
        List<Long> attachedIds = names.stream()
                .map(name -> registered.get(name).getId())
                .filter(id -> postHashtagRepository.insertIfAbsent(post.getId(), id) > 0)
                .toList();
        if (!attachedIds.isEmpty()) {
            hashtagRepository.increasePostCount(attachedIds);
        }
        return names;
    }

    /**
     * 게시물을 소프트 삭제할 때 사용 수만 줄인다. <b>연결 행은 남긴다.</b>
     *
     * <p>카테고리는 본문과 무관하게 사용자가 고른 값이라, 연결을 지우면 무엇을 골랐는지
     * 되짚을 근거가 사라져 복구할 수 없다. 삭제된 게시물은 조회 쿼리가 걸러 내므로
     * 목록·집계에는 나오지 않는다.
     */
    @Transactional
    public void detachFromPost(Long postId) {
        List<Long> hashtagIds = postHashtagRepository.findHashtagIdsByPostId(postId);
        if (hashtagIds.isEmpty()) {
            return;
        }
        hashtagRepository.decreasePostCount(hashtagIds);
    }

    /** 복구할 때 사용 수를 되돌린다. 연결 행은 삭제할 때 지우지 않았으므로 그대로 쓴다. */
    @Transactional
    public List<String> restoreForPost(Long postId) {
        List<Long> hashtagIds = postHashtagRepository.findHashtagIdsByPostId(postId);
        if (hashtagIds.isEmpty()) {
            return List.of();
        }
        hashtagRepository.increasePostCount(hashtagIds);
        return findNamesByPostIds(List.of(postId)).getOrDefault(postId, List.of());
    }

    /** 게시물을 완전히 지울 때 연결과 사용 수를 함께 없앤다. */
    @Transactional
    public void purgeForPost(Long postId) {
        List<Long> hashtagIds = postHashtagRepository.findHashtagIdsByPostId(postId);
        postHashtagRepository.deleteByPostId(postId);
        if (!hashtagIds.isEmpty()) {
            hashtagRepository.decreasePostCount(hashtagIds);
        }
    }

    /**
     * 고른 카테고리가 바뀌면 기존 연결을 지우고 새로 붙인다.
     *
     * <p>소프트 삭제와 달리 연결 행까지 지운다. 사용자가 뺀 카테고리는 되살릴 대상이
     * 아니므로, 남겨 두면 나중에 복구할 때 뺐던 것이 다시 붙는다.
     */
    @Transactional
    public List<String> reattach(Post post, List<String> requested) {
        purgeForPost(post.getId());
        return attach(post, requested);
    }

    /**
     * 등록된 태그 전체. 화면의 카테고리 탭에 그대로 쓴다.
     *
     * <p>등록 순서로 준다. 사용 수로 정렬하면 글이 쌓일 때마다 탭 순서가 바뀌어, 사용자가
     * 늘 같은 자리에서 같은 탭을 누를 수 없다.
     */
    @Transactional(readOnly = true)
    public HashtagSuggestionListResponse findAll() {
        return new HashtagSuggestionListResponse(
                hashtagRepository.findAllByOrderByIdAsc().stream()
                        .map(HashtagSuggestionResponse::from)
                        .toList());
    }

    /** 앞글자로 시작하는 태그를 많이 쓰인 순으로 추천한다. */
    @Transactional(readOnly = true)
    public HashtagSuggestionListResponse suggest(String keyword, Integer size) {
        if (keyword == null || keyword.isBlank()) {
            return new HashtagSuggestionListResponse(List.of());
        }
        int limit = size == null || size <= 0
                ? DEFAULT_SUGGESTION_SIZE
                : Math.min(size, MAX_SUGGESTION_SIZE);

        List<HashtagSuggestionResponse> items = hashtagRepository
                .findByNameStartingWithOrderByPostCountDescNameAsc(
                        keyword.trim().toLowerCase(), PageRequest.of(0, limit))
                .stream()
                .map(HashtagSuggestionResponse::from)
                .toList();
        return new HashtagSuggestionListResponse(items);
    }

    /**
     * 이 태그가 실제로 가리키는 장소들. 태그를 눌렀을 때 글 목록 대신 지도를 보여주는 데 쓴다.
     *
     * <p>설정한 인원 이상이 언급한 장소만 준다. 한 사람의 태그로 순위가 만들어지면
     * 목록을 믿을 수 없다.
     */
    @Transactional(readOnly = true)
    public HashtagPlaceListResponse findPlaces(String keyword, Integer size) {
        if (keyword == null || keyword.isBlank()) {
            return new HashtagPlaceListResponse(List.of());
        }
        int limit = size == null || size <= 0
                ? DEFAULT_PLACE_SIZE
                : Math.min(size, MAX_PLACE_SIZE);

        List<HashtagPlaceResponse> items = postHashtagRepository.findPlacesByHashtag(
                        keyword.trim().toLowerCase(),
                        minAuthorsForPlace,
                        PageRequest.of(0, limit))
                .stream()
                .map(row -> new HashtagPlaceResponse(
                        (Long) row[0],
                        (String) row[1],
                        (String) row[2],
                        (String) row[3],
                        (BigDecimal) row[4],
                        (BigDecimal) row[5],
                        (Long) row[6],
                        (Long) row[7]))
                .toList();
        return new HashtagPlaceListResponse(items);
    }

    /** 게시물별 태그 이름. 목록 응답에 붙일 때 게시물 수와 무관하게 한 번만 조회한다. */
    @Transactional(readOnly = true)
    public Map<Long, List<String>> findNamesByPostIds(Collection<Long> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        return postHashtagRepository.findPostIdAndNamePairs(postIds).stream()
                .collect(Collectors.groupingBy(
                        pair -> (Long) pair[0],
                        Collectors.mapping(pair -> (String) pair[1], Collectors.toList())));
    }

}
