package com.geo.analytics.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginCodeRequest(
    @JsonProperty("email")
    @NotBlank(message = "email must not be blank")
    @Email(message = "email must be a valid address")
    @Size(max = 320, message = "email must not exceed 320 characters")
    String email
) {
    // Why: 貼り付けで前後に空白が付きやすい。形式チェックの前に落とし、空白だけで弾かれないようにする。
    public LoginCodeRequest {
        email = email == null ? null : email.strip();
    }
}
