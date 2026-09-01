package com.server.media.service;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;

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
     * 우리가 올린 파일을 전부 훑는다. 게시물에 붙지 않고 남은 파일을 찾는 데 쓴다.
     *
     * @return 업로드 시각과 함께, {@link #upload} 가 돌려주는 것과 같은 형태의 URL
     */
    List<StoredObject> listAll();

    /**
     * @param url          공개 URL
     * @param lastModified 저장된 시각. 방금 올라온 파일을 고아로 오인하지 않으려고 본다
     */
    record StoredObject(String url, Instant lastModified) {
    }
}
