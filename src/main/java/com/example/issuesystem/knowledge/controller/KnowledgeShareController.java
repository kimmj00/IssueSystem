package com.example.issuesystem.knowledge.controller;

import com.example.issuesystem.common.ApiResponse;
import com.example.issuesystem.common.PageResponse;
import com.example.issuesystem.issue.domain.InfraType;
import com.example.issuesystem.knowledge.dto.KnowledgeShareCreateRequest;
import com.example.issuesystem.knowledge.dto.KnowledgeShareResponse;
import com.example.issuesystem.knowledge.service.KnowledgeShareService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * 지식공유 API Controller
 *
 * 기능:
 * 1. 지식공유 등록(JSON / multipart)
 * 2. 지식공유 검색
 * 3. 지식공유 단건 상세 조회
 * 4. 첨부파일 다운로드
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/knowledge-shares")
public class KnowledgeShareController {

    private final KnowledgeShareService knowledgeShareService;

    /**
     * 지식공유 등록 - 첨부파일 포함
     *
     * request: JSON 파트
     * files: 첨부파일 목록
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Long> create(
            @Valid @RequestPart("request") KnowledgeShareCreateRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        return ApiResponse.ok(knowledgeShareService.create(request, files));
    }

    /**
     * 지식공유 등록 - JSON 전용
     *
     * 첨부파일 없이 등록할 때 사용한다.
     */
    @PostMapping("/json")
    public ApiResponse<Long> createJson(
            @Valid @RequestBody KnowledgeShareCreateRequest request
    ) {
        return ApiResponse.ok(knowledgeShareService.create(request, null));
    }

    /**
     * 지식공유 검색
     *
     * 등록은 인프라 다중 선택,
     * 검색은 infraType 하나만 선택해서 포함 여부로 조회한다.
     */
    @GetMapping("/search")
    public ApiResponse<PageResponse<KnowledgeShareResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) InfraType infraType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(
                knowledgeShareService.search(
                        keyword,
                        customerName,
                        infraType,
                        startDate,
                        endDate,
                        page,
                        size
                )
        );
    }

    /**
     * 지식공유 단건 상세 조회
     *
     * 목록 클릭 후 새 창 상세보기에서 사용한다.
     */
    @GetMapping("/{id}")
    public ApiResponse<KnowledgeShareResponse> get(
            @PathVariable Long id
    ) {
        return ApiResponse.ok(knowledgeShareService.get(id));
    }

    /**
     * 첨부파일 다운로드
     *
     * 서버에 저장된 파일은 압축 후 암호화되어 있으므로,
     * Service에서 복호화/압축해제된 Resource를 받아 원본 파일명으로 내려준다.
     */
    @GetMapping("/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable Long attachmentId
    ) {
        KnowledgeShareService.DownloadFile downloadFile =
                knowledgeShareService.getDownloadFile(attachmentId);

        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(downloadFile.originalFileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .contentLength(downloadFile.fileSize())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(downloadFile.resource());
    }
}
