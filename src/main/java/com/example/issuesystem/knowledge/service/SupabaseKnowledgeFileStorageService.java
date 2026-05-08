package com.example.issuesystem.knowledge.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Render + supabase 프로파일 전용 지식공유 첨부파일 저장 서비스
 *
 * 적용 프로파일:
 * - supabase
 *
 * 저장 방식:
 * - Supabase Storage에 압축/암호화된 파일 저장
 * - DB storedPath에는 로컬 경로가 아니라 Storage object key 저장
 *
 * 사용하는 Render 환경변수:
 * - SUPABASE_URL
 * - SUPABASE_SERVICE_ROLE_KEY
 * - SUPABASE_STORAGE_BUCKET
 * - FILE_SECRET_KEY
 */
@Service
@Profile("supabase")
public class SupabaseKnowledgeFileStorageService implements KnowledgeFileStorageService {

    private static final int GCM_TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;

    private final String supabaseUrl;
    private final String serviceRoleKey;
    private final String bucketName;
    private final SecretKeySpec secretKeySpec;
    private final SecureRandom secureRandom = new SecureRandom();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public SupabaseKnowledgeFileStorageService(
            @Value("${SUPABASE_URL}") String supabaseUrl,
            @Value("${SUPABASE_SERVICE_ROLE_KEY}") String serviceRoleKey,
            @Value("${SUPABASE_STORAGE_BUCKET}") String bucketName,
            @Value("${FILE_SECRET_KEY}") String fileSecretKey
    ) {
        this.supabaseUrl = removeTrailingSlash(supabaseUrl);
        this.serviceRoleKey = serviceRoleKey;
        this.bucketName = bucketName;
        this.secretKeySpec = createSecretKey(fileSecretKey);
    }

    /**
     * 첨부파일을 Supabase Storage에 압축/암호화 저장한다.
     */
    @Override
    public StoredFileInfo store(MultipartFile file, Long knowledgeShareId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("빈 파일은 업로드할 수 없습니다.");
        }

        String originalFileName = normalizeOriginalFileName(file.getOriginalFilename());
        String storedFileName = generateOpaqueFileName();

        // Supabase Storage object key
        // DB storedPath에는 이 값을 저장한다.
        String objectKey = "knowledge/" + knowledgeShareId + "/" + storedFileName;

        try {
            byte[] encryptedBytes = compressAndEncrypt(file.getInputStream());

            uploadToSupabase(objectKey, encryptedBytes);

            return new StoredFileInfo(
                    originalFileName,
                    storedFileName,
                    objectKey,
                    file.getSize()
            );
        } catch (Exception e) {
            throw new IllegalStateException("지식공유 첨부파일 Supabase 저장에 실패했습니다.", e);
        }
    }

    /**
     * Supabase Storage에서 암호화 파일을 읽고 복호화/압축해제해서 원본 스트림으로 반환한다.
     */
    @Override
    public InputStream decryptToInputStream(String storedPath) {
        try {
            byte[] encryptedBytes = downloadFromSupabase(storedPath);

            ByteArrayInputStream encryptedInputStream = new ByteArrayInputStream(encryptedBytes);

            byte[] iv = encryptedInputStream.readNBytes(IV_LENGTH_BYTE);

            if (iv.length != IV_LENGTH_BYTE) {
                throw new IllegalArgumentException("암호화 파일 형식이 올바르지 않습니다.");
            }

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKeySpec,
                    new GCMParameterSpec(GCM_TAG_LENGTH_BIT, iv)
            );

            CipherInputStream cipherInputStream = new CipherInputStream(encryptedInputStream, cipher);

            return new GZIPInputStream(cipherInputStream);
        } catch (Exception e) {
            throw new IllegalStateException("Supabase 첨부파일 복호화/압축해제에 실패했습니다.", e);
        }
    }

    /**
     * 원본 파일을 GZIP 압축 후 AES-GCM 암호화해서 byte[]로 만든다.
     */
    private byte[] compressAndEncrypt(InputStream plainInputStream) {
        byte[] iv = new byte[IV_LENGTH_BYTE];
        secureRandom.nextBytes(iv);

        try (
                InputStream in = plainInputStream;
                ByteArrayOutputStream byteOut = new ByteArrayOutputStream()
        ) {
            // IV는 복호화에 필요하므로 암호화 데이터 앞부분에 포함한다.
            byteOut.write(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    secretKeySpec,
                    new GCMParameterSpec(GCM_TAG_LENGTH_BIT, iv)
            );

            try (
                    CipherOutputStream cipherOut = new CipherOutputStream(byteOut, cipher);
                    GZIPOutputStream gzipOut = new GZIPOutputStream(cipherOut)
            ) {
                in.transferTo(gzipOut);
            }

            return byteOut.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("첨부파일 압축/암호화 처리에 실패했습니다.", e);
        }
    }

    /**
     * Supabase Storage에 object 업로드
     */
    private void uploadToSupabase(String objectKey, byte[] encryptedBytes) {
        try {
            String uploadUrl = supabaseUrl
                    + "/storage/v1/object/"
                    + urlEncodePath(bucketName)
                    + "/"
                    + urlEncodePath(objectKey);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uploadUrl))
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .header("apikey", serviceRoleKey)
                    .header("Content-Type", "application/octet-stream")
                    .header("x-upsert", "true")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(encryptedBytes))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Supabase Storage 업로드 실패. status="
                                + response.statusCode()
                                + ", body="
                                + response.body()
                );
            }
        } catch (Exception e) {
            throw new IllegalStateException("Supabase Storage 업로드 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * Supabase Storage에서 object 다운로드
     */
    private byte[] downloadFromSupabase(String objectKey) {
        try {
            String downloadUrl = supabaseUrl
                    + "/storage/v1/object/"
                    + urlEncodePath(bucketName)
                    + "/"
                    + urlEncodePath(objectKey);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .header("apikey", serviceRoleKey)
                    .GET()
                    .build();

            HttpResponse<byte[]> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofByteArray()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Supabase Storage 다운로드 실패. status="
                                + response.statusCode()
                );
            }

            return response.body();
        } catch (Exception e) {
            throw new IllegalStateException("Supabase Storage 다운로드 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * Storage object path URL 인코딩
     *
     * slash(/)는 경로 구분자로 유지하고,
     * 각 segment만 인코딩한다.
     */
    private String urlEncodePath(String path) {
        String[] parts = path.split("/");
        StringBuilder encoded = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                encoded.append("/");
            }

            encoded.append(
                    URLEncoder.encode(parts[i], StandardCharsets.UTF_8)
                            .replace("+", "%20")
            );
        }

        return encoded.toString();
    }

    /**
     * URL 마지막 slash 제거
     */
    private String removeTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Supabase URL은 필수입니다.");
        }

        String trimmed = value.trim();

        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }

        return trimmed;
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
     * 브라우저/OS별 경로 구분자가 섞인 원본 파일명을 정리한다.
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
}