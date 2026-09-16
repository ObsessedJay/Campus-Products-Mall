package com.sk.onlinemall.user.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class UserEntity {
    private Long id;
    private String username;
    private String email;
    @JsonIgnore
    private String passwordHash;
    private String nickname;
    private String school;
    private String studentNo;
    private UserRole role;
    private UserStatus status;
    private Integer creditScore;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
