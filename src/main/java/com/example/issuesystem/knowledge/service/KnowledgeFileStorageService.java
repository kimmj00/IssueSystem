package com.example.issuesystem.knowledge.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 지식공유 첨부파일 저장 인터페이스
 *
 * 목적:
 * - local / deploy 환경에서는 서버 로컬 디스크에 저장
 * - supabase(Render) 환경에서는 Supabase Storage에 저장
 *
 * KnowledgeShareService는 이 인터페이스만 바라본다.
 * 실제 구현체는 Spring Profile에 따라 자동 선택된다.
 */
public interface KnowledgeFileStorageService {

    /**
     * 첨부파일 저장
     *
     * @param file 업로드된 파일
     * @param knowledgeShareId 지식공유 글 ID
     * @return DB에 기록할 파일 정보
     */
    StoredFileInfo store(MultipartFile file, Long knowledgeShareId);

    /**
     * 저장된 파일을 원본 InputStream으로 반환
     *
     * local / deploy:
     * - storedPath = 실제 서버 파일 경로
     *
     * supabase:
     * - storedPath = Supabase Storage object key
     */
    InputStream decryptToInputStream(String storedPath);

    /** 저장된 첨부파일을 삭제한다. */
    void delete(String storedPath);

    /**
     * 저장된 파일 정보
     *
     * storedPath 의미:
     * - local / deploy: 로컬 디스크 실제 경로
     * - supabase: Supabase Storage object key
     */
    record StoredFileInfo(
            String originalFileName,
            String storedFileName,
            String storedPath,
            Long fileSize
    ) {
    }
}
