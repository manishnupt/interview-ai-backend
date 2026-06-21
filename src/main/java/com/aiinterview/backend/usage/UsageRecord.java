package com.aiinterview.backend.usage;

import com.aiinterview.backend.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "usage_records")
@Getter
@Setter
@NoArgsConstructor
public class UsageRecord extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "candidate_id")
    private Long candidateId;

    @Column(name = "call_sid")
    private String callSid;

    @Column(name = "usage_type", nullable = false)
    private String usageType; // "interview" or "screening"

    @Column(name = "rate_card_version")
    private String rateCardVersion;

    @Column(name = "duration_seconds")
    private Double durationSeconds = 0.0;

    @Column(name = "tts_characters")
    private Integer ttsCharacters = 0;

    @Column(name = "stt_words")
    private Integer sttWords = 0;

    @Column(name = "llm_input_tokens")
    private Integer llmInputTokens = 0;

    @Column(name = "llm_output_tokens")
    private Integer llmOutputTokens = 0;

    @Column(name = "gpt_call_count")
    private Integer gptCallCount = 0;

    @Column(name = "questions_asked")
    private Integer questionsAsked = 0;

    @Column(name = "outcome")
    private String outcome;

    @Column(name = "twilio_cost_usd")
    private BigDecimal twilioCostUsd = BigDecimal.ZERO;

    @Column(name = "deepgram_cost_usd")
    private BigDecimal deepgramCostUsd = BigDecimal.ZERO;

    @Column(name = "elevenlabs_cost_usd")
    private BigDecimal elevenlabsCostUsd = BigDecimal.ZERO;

    @Column(name = "openai_cost_usd")
    private BigDecimal openaiCostUsd = BigDecimal.ZERO;

    @Column(name = "total_cost_usd", nullable = false)
    private BigDecimal totalCostUsd = BigDecimal.ZERO;
}
