package com.server.hashtag.service;

import com.server.hashtag.domain.Hashtag;
import com.server.hashtag.domain.PostHashtag;
import com.server.hashtag.dto.HashtagSuggestionListResponse;
import com.server.hashtag.dto.HashtagSuggestionResponse;
import com.server.hashtag.repository.HashtagRepository;
import com.server.hashtag.repository.PostHashtagRepository;
import com.server.post.domain.Post;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HashtagService {

    private static final int DEFAULT_SUGGESTION_SIZE = 10;
    private static final int MAX_SUGGESTION_SIZE = 30;

    private final HashtagRepository hashtagRepository;
    private final PostHashtagRepository postHashtagRepository;
    private final HashtagExtractor hashtagExtractor;

    public HashtagService(
            HashtagRepository hashtagRepository,
            PostHashtagRepository postHashtagRepository,
            HashtagExtractor hashtagExtractor
    ) {
        this.hashtagRepository = hashtagRepository;
        this.postHashtagRepository = postHashtagRepository;
        this.hashtagExtractor = hashtagExtractor;
    }

    /** 본문에서 태그를 뽑아 게시물에 연결하고 사용 수를 올린다. */
    @Transactional
    public void attachFromContent(Post post, String content) {
        List<String> names = hashtagExtractor.extract(content);
        if (names.isEmpty()) {
            return;
        }

        List<Hashtag> hashtags = findOrCreate(names);
        postHashtagRepository.saveAll(hashtags.stream()
                .map(hashtag -> new PostHashtag(post, hashtag))
                .toList());
        hashtagRepository.increasePostCount(hashtags.stream().map(Hashtag::getId).toList());
    }

    /** 게시물이 삭제되면 연결을 끊고 사용 수를 되돌린다. */
    @Transactional
    public void detachFromPost(Long postId) {
        List<Long> hashtagIds = postHashtagRepository.findHashtagIdsByPostId(postId);
        if (hashtagIds.isEmpty()) {
            return;
        }
        postHashtagRepository.deleteByPostId(postId);
        hashtagRepository.decreasePostCount(hashtagIds);
    }

    /** 게시물 본문이 바뀌면 태그를 다시 계산한다. */
    @Transactional
    public void reattachFromContent(Post post, String content) {
        detachFromPost(post.getId());
        attachFromContent(post, content);
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

    /**
     * 이미 있는 태그는 그대로 쓰고 없는 것만 만든다. 같은 태그를 두 사람이 동시에 처음
     * 쓰면 이름 고유 제약에 걸릴 수 있으며, 그 경우 요청 하나가 실패한다.
     */
    private List<Hashtag> findOrCreate(List<String> names) {
        Map<String, Hashtag> existing = hashtagRepository.findByNameIn(names).stream()
                .collect(Collectors.toMap(Hashtag::getName, Function.identity()));

        List<Hashtag> created = new ArrayList<>();
        for (String name : names) {
            if (!existing.containsKey(name)) {
                created.add(new Hashtag(name));
            }
        }
        if (!created.isEmpty()) {
            hashtagRepository.saveAll(created).forEach(hashtag -> existing.put(hashtag.getName(), hashtag));
        }
        return names.stream().map(existing::get).toList();
    }
}
