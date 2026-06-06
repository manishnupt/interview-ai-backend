package com.aiinterview.backend.files;

import com.aiinterview.backend.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
public class S3FileService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final List<String> ALLOWED_TYPES = List.of(
        "application/pdf",
        "application/octet-stream"
    );

    public String uploadResume(MultipartFile file, Long candidateId, Long jobId) {
        validateFile(file);

        String key = String.format("resumes/%d/%d/%d-%s.pdf",
            jobId, candidateId, candidateId,
            UUID.randomUUID().toString().substring(0, 8));

        System.out.println("[S3 STUB] Would upload to: " + key);
        System.out.println("[S3 STUB] File: " + file.getOriginalFilename()
            + " (" + file.getSize() + " bytes)");

        return key;
    }

    public String generatePresignedUrl(String s3Key) {
        if (s3Key == null || s3Key.isBlank()) return null;
        String url = "https://stub-s3-bucket.s3.ap-south-1.amazonaws.com/"
            + s3Key + "?stub=true";
        System.out.println("[S3 STUB] Presigned URL for: " + s3Key);
        return url;
    }

    public void deleteResume(String s3Key) {
        System.out.println("[S3 STUB] Would delete: " + s3Key);
    }

    public void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Resume file is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("Resume file too large. Maximum size is 5MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.contains("pdf")) {
            throw new BusinessException("Only PDF resumes are accepted");
        }
    }
}
