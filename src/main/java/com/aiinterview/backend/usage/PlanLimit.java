package com.aiinterview.backend.usage;

import com.aiinterview.backend.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "plan_limits")
@Getter
@Setter
@NoArgsConstructor
public class PlanLimit extends BaseEntity {

    @Column(name = "company_id", nullable = false, unique = true)
    private Long companyId;

    @Column(name = "monthly_interview_cap")
    private Integer monthlyInterviewCap = 50;

    @Column(name = "monthly_screening_cap")
    private Integer monthlyScreeningCap = 500;

    @Column(name = "monthly_cost_cap_usd")
    private BigDecimal monthlyCostCapUsd = new BigDecimal("50.00");

    @Column(name = "alert_threshold_pct")
    private Integer alertThresholdPct = 80;
}
