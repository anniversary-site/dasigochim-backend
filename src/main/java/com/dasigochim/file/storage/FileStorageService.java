package com.dasigochim.file.storage;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    StoredFile upload(MultipartFile file);

    /** 서버가 생성한 문서(HWP 등)를 업로드 저장소에 보관한다. */
    StoredFile store(byte[] bytes, String originalFilename, String mimeType);

    /** 업로드된 파일을 다시 읽는다. AI 서버에 사진을 base64 로 넘길 때 쓴다. */
    LoadedFile load(String url);

    record StoredFile(String url, String originalFilename, String mimeType, long sizeBytes) {}
    record LoadedFile(byte[] bytes, String mimeType) {}
}
