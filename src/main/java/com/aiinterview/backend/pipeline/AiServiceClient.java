package com.aiinterview.backend.pipeline;

import com.aiinterview.backend.common.BusinessException;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Service
public class AiServiceClient {

    @Value("${app.python-ai-service.url}")
    private String pythonServiceUrl;

    private final WebClient webClient;

    public AiServiceClient(WebClient.Builder builder) {
        this.webClient = builder
            .codecs(c -> c.defaultCodecs()
                .maxInMemorySize(2 * 1024 * 1024))
            .build();
    }

    // ── DTOs for Python service communication ──

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ScreenRequest {
        private Long candidateId;
        private String resumeUrl;
        private String jobDescription;
        private Long jobId;
        private Long companyId;
    }

    @Data @NoArgsConstructor
    public static class ScreenResponse {
        private Long candidateId;
        private Boolean fit;
        private Integer score;
        private Integer matchPercentage;
        private List<String> fitReasons;
        private List<String> concerns;
        private List<String> missingSkills;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class InterviewRequest {
        private Long candidateId;
        private String phone;
        private String candidateName;
        private String resumeUrl;
        private String jobDescription;
        private Long jobId;
        private Long companyId;
    }

    @Data @NoArgsConstructor
    public static class InterviewTriggerResponse {
        private Long candidateId;
        private String callSid;
        private String status;
    }

    // ── API calls ──

    public ScreenResponse screenResume(ScreenRequest request) {
        System.out.println("[AiClient] Calling Python /screen for candidate: "
            + request.getCandidateId());
        try {
            return webClient.post()
                .uri(pythonServiceUrl + "/screen")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(),
                    resp -> resp.bodyToMono(String.class)
                        .map(body -> new BusinessException("AI screening failed: " + body)))
                .onStatus(status -> status.is5xxServerError(),
                    resp -> resp.bodyToMono(String.class)
                        .map(body -> new BusinessException("AI service error: " + body)))
                .bodyToMono(ScreenResponse.class)
                .timeout(java.time.Duration.ofSeconds(120))
                .block();
        } catch (Exception e) {
            System.out.println("[AiClient] Screening call failed: " + e.getMessage());
            throw new BusinessException("Resume screening service unavailable");
        }
    }

    public InterviewTriggerResponse triggerInterview(InterviewRequest request) {
        System.out.println("[AiClient] Calling Python /interview for candidate: "
            + request.getCandidateId());
        try {
            return webClient.post()
                .uri(pythonServiceUrl + "/interview")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(InterviewTriggerResponse.class)
                .timeout(java.time.Duration.ofSeconds(30))
                .block();
        } catch (Exception e) {
            System.out.println("[AiClient] Interview trigger failed: " + e.getMessage());
            throw new BusinessException("Interview service unavailable");
        }
    }

    public boolean isHealthy() {
        try {
            String result = webClient.get()
                .uri(pythonServiceUrl + "/health")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(java.time.Duration.ofSeconds(5))
                .block();
            return result != null && result.contains("ok");
        } catch (Exception e) {
            return false;
        }
    }
}
