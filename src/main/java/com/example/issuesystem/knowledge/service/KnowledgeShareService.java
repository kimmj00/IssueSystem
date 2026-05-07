package com.example.issuesystem.knowledge.service;

import com.example.issuesystem.common.PageResponse;
import com.example.issuesystem.issue.domain.InfraType;
import com.example.issuesystem.knowledge.domain.KnowledgeShare;
import com.example.issuesystem.knowledge.domain.KnowledgeShareAttachment;
import com.example.issuesystem.knowledge.dto.KnowledgeShareCreateRequest;
import com.example.issuesystem.knowledge.dto.KnowledgeShareResponse;
import com.example.issuesystem.knowledge.repository.KnowledgeShareAttachmentRepository;
import com.example.issuesystem.knowledge.repository.KnowledgeShareRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 지식공유 서비스
 */
@Service
@RequiredArgsConstructor
public class KnowledgeShareService {

    private final KnowledgeShareRepository knowledgeShareRepository;
    private final KnowledgeShareAttachmentRepository attachmentRepository;
    private final KnowledgeFileStorageService fileStorageService;

    /**
     * 지식공유 등록
     *
     * 1. 본문 먼저 저장
     * 2. 저장된 ID 기준으로 첨부파일 디렉터리 생성
     * 3. 첨부파일 저장 후 attachment 테이블 기록
     */
    @Transactional
    public Long create(KnowledgeShareCreateRequest request, List<MultipartFile> files) {
        KnowledgeShare knowledgeShare = KnowledgeShare.builder()
                .title(request.getTitle())
                .customerName(request.getCustomerName())
                .authorName(request.getAuthorName())
                .attachmentName(request.getAttachmentName())
                .content(request.getContent())
                .infraTypes(request.getInfraTypes())
                .build();

        KnowledgeShare saved = knowledgeShareRepository.save(knowledgeShare);

        for (MultipartFile file : emptyIfNull(files)) {
            if (file == null || file.isEmpty()) {
                continue;
            }

            KnowledgeFileStorageService.StoredFileInfo stored =
                    fileStorageService.store(file, saved.getId());

            KnowledgeShareAttachment attachment = KnowledgeShareAttachment.builder()
                    .knowledgeShare(saved)
                    .originalFileName(stored.originalFileName())
                    .storedFileName(stored.storedFileName())
                    .storedPath(stored.storedPath())
                    .fileSize(stored.fileSize())
                    .build();

            attachmentRepository.save(attachment);
        }

        return saved.getId();
    }

    /**
     * 지식공유 검색
     *
     * 등록은 인프라 다중 선택이지만,
     * 검색은 infraType 하나만 받아서 해당 인프라가 포함된 글을 조회한다.
     */
    @Transactional
    public PageResponse<KnowledgeShareResponse> search(
            String keyword,
            String customerName,
            InfraType infraType,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 10 : size;

        /*
         * PostgreSQL은 :startDate is null 같은 조건에서
         * null 파라미터 타입을 추론하지 못할 수 있다.
         * 그래서 Repository에는 날짜 null을 넘기지 않는다.
         */
        LocalDateTime startDateTime = startDate != null
                ? startDate.atStartOfDay()
                : LocalDateTime.of(1970, 1, 1, 0, 0);

        LocalDateTime endDateTime = endDate != null
                ? endDate.plusDays(1).atStartOfDay()
                : LocalDateTime.of(9999, 12, 31, 0, 0);

        Page<KnowledgeShare> pageResult = knowledgeShareRepository.search(
                keyword,
                customerName,
                infraType,
                startDateTime,
                endDateTime,
                PageRequest.of(safePage, safeSize)
        );

        Page<KnowledgeShareResponse> responsePage = pageResult.map(knowledgeShare -> {
            List<KnowledgeShareAttachment> attachments =
                    attachmentRepository.findByKnowledgeShareId(knowledgeShare.getId());

            return KnowledgeShareResponse.from(knowledgeShare, attachments);
        });

        return PageResponse.from(responsePage);
    }

    /**
     * 지식공유 단건 상세 조회
     *
     * 목록 행 클릭 후 새 창에서 상세 내용을 조회할 때 사용한다.
     */
    @Transactional
    public KnowledgeShareResponse get(Long id) {
        KnowledgeShare knowledgeShare = knowledgeShareRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("지식공유 글을 찾을 수 없습니다."));

        List<KnowledgeShareAttachment> attachments =
                attachmentRepository.findByKnowledgeShareId(id);

        return KnowledgeShareResponse.from(knowledgeShare, attachments);
    }

    /**
     * 첨부파일 다운로드 정보 조회
     */
    @Transactional
    public DownloadFile getDownloadFile(Long attachmentId) {
        KnowledgeShareAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("첨부파일을 찾을 수 없습니다."));

        Path path = Paths.get(attachment.getStoredPath()).toAbsolutePath().normalize();

        try {
            Resource resource = new UrlResource(path.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalArgumentException("첨부파일을 읽을 수 없습니다.");
            }

            return new DownloadFile(
                    resource,
                    attachment.getOriginalFileName(),
                    attachment.getFileSize()
            );
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("첨부파일 경로가 올바르지 않습니다.", e);
        }
    }

    private List<MultipartFile> emptyIfNull(List<MultipartFile> files) {
        return files == null ? Collections.emptyList() : files;
    }

    /**
     * 다운로드 응답에 필요한 파일 정보
     */
    public record DownloadFile(
            Resource resource,
            String originalFileName,
            Long fileSize
    ) {
    }
}