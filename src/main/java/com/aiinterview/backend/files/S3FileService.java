package com.aiinterview.backend.files;

import com.aiinterview.backend.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@Service
public class S3FileService {

    private static final Logger log = LoggerFactory.getLogger(S3FileService.class);
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private S3Presigner s3Presigner;

    @Value("${app.aws.bucket}")
    private String bucketName;

    public String uploadResume(MultipartFile file,
                               Long candidateId,
                               Long jobId) {
        validateFile(file);

        String key = String.format("resumes/%d/%d/%d-%s.pdf",
            jobId, candidateId, candidateId,
            UUID.randomUUID().toString().substring(0, 8));

        log.info("[S3] uploadResume — bucket='{}' key='{}' sizeBytes={} contentType='{}'",
            bucketName, key, file.getSize(), file.getContentType());

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType("application/pdf")
                .contentLength(file.getSize())
                .build();

            s3Client.putObject(putRequest,
                RequestBody.fromInputStream(
                    file.getInputStream(),
                    file.getSize()));

            log.info("[S3] Upload succeeded — key='{}' sizeBytes={}", key, file.getSize());
            return key;

        } catch (S3Exception e) {
            String errorCode    = e.awsErrorDetails().errorCode();
            String errorMessage = e.awsErrorDetails().errorMessage();
            int    httpStatus   = e.statusCode();
            String requestId    = e.requestId();
            // x-amz-bucket-region tells you the ACTUAL bucket region on a PermanentRedirect
            String actualRegion = e.awsErrorDetails()
                .sdkHttpResponse()
                .firstMatchingHeader("x-amz-bucket-region")
                .orElse("header-not-present");

            log.error("[S3] Upload failed — bucket='{}' configuredRegion='{}' " +
                      "errorCode='{}' httpStatus={} requestId='{}' " +
                      "actualBucketRegion='{}' message='{}'",
                bucketName, e.awsErrorDetails().serviceName(),
                errorCode, httpStatus, requestId,
                actualRegion, errorMessage);

            throw new BusinessException("Failed to upload resume: " + errorMessage);

        } catch (IOException e) {
            log.error("[S3] Failed to read resume stream — candidateId={} jobId={} error='{}'",
                candidateId, jobId, e.getMessage(), e);
            throw new BusinessException("Failed to read resume file: " + e.getMessage());
        }
    }

    public String generatePresignedUrl(String s3Key) {
        if (s3Key == null || s3Key.isBlank()) return null;

        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(1))
                .getObjectRequest(getRequest)
                .build();

            PresignedGetObjectRequest presigned =
                s3Presigner.presignGetObject(presignRequest);

            String url = presigned.url().toString();
            log.info("[S3] Presigned URL generated — key='{}'", s3Key);
            return url;

        } catch (AwsServiceException e) {
            log.error("[S3] Presign failed — key='{}' error='{}'", s3Key, e.getMessage());
            throw new BusinessException(
                "Failed to generate resume URL: " + e.getMessage());
        }
    }

    public void deleteResume(String s3Key) {
        if (s3Key == null || s3Key.isBlank()) return;

        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

            s3Client.deleteObject(deleteRequest);
            log.info("[S3] Deleted — key='{}'", s3Key);

        } catch (S3Exception e) {
            log.error("[S3] Delete failed — key='{}' error='{}'",
                s3Key, e.awsErrorDetails().errorMessage());
        }
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
