package com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.consultation.dto;

import lombok.Data;

import java.util.List;

@Data
public class DashScopeRequest {
    private String model;
    private List<DashScopeMessage> messages;
    private boolean stream = true;
    private double top_p = 0.8;
    private double temperature = 0.7;
    private boolean enable_search = false;
    private boolean enable_thinking = false;
    private Integer thinking_budget;
    private String result_format = "message";

    @Data
    public static class DashScopeMessage {
        private String role;
        private String content;
        
        public DashScopeMessage() {}
        public DashScopeMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}
