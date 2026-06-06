package com.aiinterview.backend.candidates.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AddNoteRequest {

    @NotBlank(message = "Note content is required")
    @Size(max = 2000)
    private String content;
}
