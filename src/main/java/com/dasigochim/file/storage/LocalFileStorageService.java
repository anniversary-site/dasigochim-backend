package com.dasigochim.file.storage;

import com.dasigochim.common.exception.BusinessException;
import com.dasigochim.common.exception.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LocalFileStorageService implements FileStorageService {
    private static final String URL_PREFIX = "/files/";
    private final Path uploadDirectory;

    public LocalFileStorageService(@Value("${app.file.upload-dir}") String uploadDirectory) {
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
    }

    @Override
    public StoredFile upload(MultipartFile file) {
        String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "image" : file.getOriginalFilename());
        String extension = original.lastIndexOf('.') >= 0 ? original.substring(original.lastIndexOf('.')) : "";
        String storedName = UUID.randomUUID() + extension;
        try {
            Files.createDirectories(uploadDirectory);
            Files.copy(file.getInputStream(), uploadDirectory.resolve(storedName), StandardCopyOption.REPLACE_EXISTING);
            return new StoredFile(URL_PREFIX + storedName, original, file.getContentType(), file.getSize());
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR);
        }
    }

    @Override
    public StoredFile store(byte[] bytes, String originalFilename, String mimeType) {
        String original = StringUtils.cleanPath(originalFilename == null ? "file" : originalFilename);
        String extension = original.lastIndexOf('.') >= 0 ? original.substring(original.lastIndexOf('.')) : "";
        String storedName = UUID.randomUUID() + extension;
        try {
            Files.createDirectories(uploadDirectory);
            Files.write(uploadDirectory.resolve(storedName), bytes);
            return new StoredFile(URL_PREFIX + storedName, original, mimeType, bytes.length);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR);
        }
    }

    @Override
    public LoadedFile load(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR, "읽을 수 없는 파일 경로입니다: " + url);
        }
        Path path = uploadDirectory.resolve(url.substring(URL_PREFIX.length())).normalize();
        if (!path.startsWith(uploadDirectory) || !Files.isRegularFile(path)) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR, "파일을 찾을 수 없습니다: " + url);
        }
        try {
            String mimeType = Files.probeContentType(path);
            return new LoadedFile(Files.readAllBytes(path), mimeType == null ? "image/jpeg" : mimeType);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR);
        }
    }
}
