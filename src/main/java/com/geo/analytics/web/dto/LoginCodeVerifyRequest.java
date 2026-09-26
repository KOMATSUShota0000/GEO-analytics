package com.geo.analytics.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginCodeVerifyRequest(
    @JsonProperty("email")
    @NotBlank(message = "email must not be blank")
    @Email(message = "email must be a valid address")
    @Size(max = 320, message = "email must not exceed 320 characters")
    String email,
    @JsonProperty("code")
    @NotBlank(message = "code must not be blank")
    @Pattern(regexp = "[0-9]{6}", message = "code must be 6 digits")
    String code
) {
    // Why: 貼り付けの前後の空白と、日本語入力のままの全角数字（０〜９）で弾かれないようにする。
    //      画面側（AUTH-4）でも直すが、API を直接呼ばれても同じ扱いにする。
    public LoginCodeVerifyRequest {
        email = email == null ? null : email.strip();
        code = code == null ? null : toAsciiDigits(code.strip());
    }

    private static String toAsciiDigits(String value) {
        StringBuilder ascii = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            ascii.append(c >= '０' && c <= '９' ? (char) (c - '０' + '0') : c);
        }
        return ascii.toString();
    }
}
