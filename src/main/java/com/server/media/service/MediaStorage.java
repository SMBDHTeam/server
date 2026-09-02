package com.server.media.service;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/**
 * 파일을 어디에 두는지 감춘다. 지금 구현은 S3 하나뿐이지만, 저장소를 바꿀 때 서비스 계층이
 * 함께 흔들리지 않게 경계를 둔다.
 */
public interface MediaStorage {

    /**
     * @return 브라우저가 그대로 열 수 있는 공개 URL
     */
    String upload(String key, String contentType, InputStream content, long size);

    /**
     * 업로드할 때 돌려준 URL 로 파일 하나를 지운다.
     *
     * <p>우리 저장소의 주소가 아니면 아무것도 하지 않는다. 인증 도입 전에 만든 게시물에는
     * 외부 URL 이나 {@code blob:} 주소가 들어 있어, 그것까지 지우려 들면 안 된다.
     */
    void delete(String url);

    /**
     * 우리가 올린 파일을 페이지 단위로 훑는다. 게시물에 붙지 않고 남은 파일을 찾는 데 쓴다.
     *
     * <p>목록을 통째로 돌려주지 않는 것은 파일이 수만 건으로 늘었을 때를 위해서다. 전부
     * 메모리에 올리면 정리하다가 서버가 죽는다. 한 페이지를 처리하고 버리면 사용량이
     * 파일 수와 무관하게 일정하다.
     *
     * @param pageConsumer 한 페이지분을 받는다. 비어 있는 페이지는 넘기지 않는다
     */
    void forEachPage(Consumer<List<StoredObject>> pageConsumer);

    /**
     * @param url          공개 URL
     * @param lastModified 저장된 시각. 방금 올라온 파일을 고아로 오인하지 않으려고 본다
     */
    record StoredObject(String url, Instant lastModified) {
    }
}
