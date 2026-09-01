package com.server.media.service;

import java.io.InputStream;

/**
 * 파일을 어디에 두는지 감춘다. 지금 구현은 S3 하나뿐이지만, 저장소를 바꿀 때 서비스 계층이
 * 함께 흔들리지 않게 경계를 둔다.
 */
public interface MediaStorage {

    /**
     * @return 브라우저가 그대로 열 수 있는 공개 URL
     */
    String upload(String key, String contentType, InputStream content, long size);
}
