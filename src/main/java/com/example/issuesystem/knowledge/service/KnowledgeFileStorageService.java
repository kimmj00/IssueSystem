package com.example.issuesystem.knowledge.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 지식공유 첨부파일 저장 서비스
 *
 * 저장 방식:
 * 1. 저장 파일명에는 원본 확장자를 붙이지 않는다.
 * 2. 파일 내용을 GZIP으로 압축한다.
 * 3. 압축된 데이터를 AES-GCM으로 암호화한다.
 * 4. 다운로드 시 복호화 후 압축해제해서 원본 파일명으로 내려준다.
 */
@Service
public class KnowledgeFileStorageService {

    private static final int GCM_TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;

    private final Path uploadRoot;
    private final SecretKeySpec secretKeySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public KnowledgeFileStorageService(
            @Value("${app.file.upload-dir}") String uploadDir,
            @Value("${app.crypto.file-secret-key:issue-system-local-dev-secret-key-32}") String fileSecretKey
    ) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.secretKeySpec = createSecretKey(fileSecretKey);

        try {
            Files.createDirectories(this.uploadRoot);
        } catch (Exception e) {
            throw new IllegalStateException("업로드 디렉터리를 생성할 수 없습니다.", e);
        }
    }

    /**
     * 첨부파일 저장
     *
     * 기존 방식:
     * UUID + 원본 확장자 형태로 원본 파일을 그대로 저장
     *
     * 변경 방식:
     * 확장자 없는 랜덤 파일명으로 압축 후 암호화 저장
     */
    public StoredFileInfo store(MultipartFile file, Long knowledgeShareId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("빈 파일은 업로드할 수 없습니다.");
        }

        String originalFileName = normalizeOriginalFileName(file.getOriginalFilename());
        String storedFileName = generateOpaqueFileName();

        Path knowledgeDir = uploadRoot
                .resolve("knowledge")
                .resolve(String.valueOf(knowledgeShareId));

        try {
            Files.createDirectories(knowledgeDir);

            Path target = knowledgeDir.resolve(storedFileName);

            compressEncryptAndSave(file.getInputStream(), target);

            return new StoredFileInfo(
                    originalFileName,
                    storedFileName,
                    target.toString(),
                    file.getSize()
            );
        } catch (Exception e) {
            throw new IllegalStateException("지식공유 첨부파일 저장에 실패했습니다.", e);
        }
    }

    /**
     * 암호화·압축 저장된 파일을 원본 InputStream으로 반환한다.
     *
     * 파일 구조:
     * 앞 12바이트: IV
     * 이후 바이트: AES-GCM으로 암호화된 GZIP 데이터
     */
    public InputStream decryptToInputStream(String storedPath) {
        try {
            InputStream fileInputStream = Files.newInputStream(
                    Paths.get(storedPath).toAbsolutePath().normalize(),
                    StandardOpenOption.READ
            );

            byte[] iv = fileInputStream.readNBytes(IV_LENGTH_BYTE);

            if (iv.length != IV_LENGTH_BYTE) {
                throw new IllegalArgumentException("암호화 파일 형식이 올바르지 않습니다.");
            }

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKeySpec,
                    new GCMParameterSpec(GCM_TAG_LENGTH_BIT, iv)
            );

            CipherInputStream cipherInputStream = new CipherInputStream(fileInputStream, cipher);

            // 저장 파일 → AES 복호화 → GZIP 압축해제 → 원본 반환
            return new GZIPInputStream(cipherInputStream);
        } catch (Exception e) {
            throw new IllegalStateException("첨부파일 복호화/압축해제에 실패했습니다.", e);
        }
    }

    /**
     * 첨부파일을 압축 후 암호화해서 저장한다.
     *
     * 처리 순서:
     * 원본 파일 읽기 → GZIP 압축 → AES-GCM 암호화 → 확장자 없는 파일로 저장
     */
    private void compressEncryptAndSave(InputStream plainInputStream, Path target) {
        byte[] iv = new byte[IV_LENGTH_BYTE];
        secureRandom.nextBytes(iv);

        try (
                InputStream in = plainInputStream;
                OutputStream fileOut = Files.newOutputStream(
                        target,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE
                )
        ) {
            // IV는 비밀값이 아니며, 복호화에 필요하므로 파일 앞부분에 저장한다.
            fileOut.write(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    secretKeySpec,
                    new GCMParameterSpec(GCM_TAG_LENGTH_BIT, iv)
            );

            try (
                    CipherOutputStream cipherOut = new CipherOutputStream(fileOut, cipher);
                    GZIPOutputStream gzipOut = new GZIPOutputStream(cipherOut)
            ) {
                in.transferTo(gzipOut);
            }
        } catch (Exception e) {
            throw new IllegalStateException("첨부파일 압축/암호화 저장에 실패했습니다.", e);
        }
    }

    /**
     * 확장자 없는 랜덤 저장 파일명 생성
     */
    private String generateOpaqueFileName() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);

        return HexFormat.of().formatHex(randomBytes);
    }

    /**
     * 원본 파일명이 없거나 경로 구분자가 섞인 경우 방어적으로 정리한다.
     */
    private String normalizeOriginalFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "unknown-file";
        }

        String normalized = originalFileName.replace("\\", "/");
        int lastSlashIndex = normalized.lastIndexOf('/');

        if (lastSlashIndex >= 0) {
            return normalized.substring(lastSlashIndex + 1);
        }

        return normalized;
    }

    /**
     * 문자열 키를 SHA-256으로 해시해서 AES-256 키로 사용한다.
     *
     * 운영에서는 app.crypto.file-secret-key를 반드시 환경변수로 관리해야 한다.
     */
    private SecretKeySpec createSecretKey(String fileSecretKey) {
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                    .digest(fileSecretKey.getBytes(StandardCharsets.UTF_8));

            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("파일 암호화 키 생성에 실패했습니다.", e);
        }
    }

    /**
     * 저장된 파일 정보
     *
     * fileSize는 원본 파일 크기다.
     * 실제 디스크에 저장되는 압축/암호화 파일 크기와 다를 수 있다.
     */
    public record StoredFileInfo(
            String originalFileName,
            String storedFileName,
            String storedPath,
            Long fileSize
    ) {
    }
}
