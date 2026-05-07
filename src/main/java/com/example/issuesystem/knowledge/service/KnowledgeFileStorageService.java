package com.example.issuesystem.knowledge.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

/**
 * 지식공유 첨부파일 저장 서비스
 *
 * app.file.upload-dir 하위에 knowledge/{지식공유ID}/ 형태로 저장한다.
 */
@Service
public class KnowledgeFileStorageService {

    private final Path uploadRoot;

    public KnowledgeFileStorageService(@Value("${app.file.upload-dir}") String uploadDir) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.uploadRoot);
        } catch (IOException e) {
            throw new IllegalStateException("업로드 디렉터리를 생성할 수 없습니다.", e);
        }
    }

    /**
     * 첨부파일 저장
     */
    public StoredFileInfo store(MultipartFile file, Long knowledgeShareId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("빈 파일은 업로드할 수 없습니다.");
        }

        String originalFileName = file.getOriginalFilename();
        String extension = extractExtension(originalFileName);

        String storedFileName = UUID.randomUUID()
                + (extension.isBlank() ? "" : "." + extension);

        Path knowledgeDir = uploadRoot
                .resolve("knowledge")
                .resolve(String.valueOf(knowledgeShareId));

        try {
            Files.createDirectories(knowledgeDir);

            Path target = knowledgeDir.resolve(storedFileName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            return new StoredFileInfo(
                    originalFileName,
                    storedFileName,
                    target.toString(),
                    file.getSize()
            );
        } catch (IOException e) {
            throw new IllegalStateException("지식공유 첨부파일 저장에 실패했습니다.", e);
        }
    }

    /**
     * 파일 확장자 추출
     */
    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }

        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }

    /**
     * 저장된 파일 정보
     */
    public record StoredFileInfo(
            String originalFileName,
            String storedFileName,
            String storedPath,
            Long fileSize
    ) {
    }
}