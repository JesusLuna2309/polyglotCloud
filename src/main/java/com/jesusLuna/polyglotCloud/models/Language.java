package com.jesusLuna.polyglotCloud.models;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Entity
@Table(name = "languages")
@Data
@Builder
public class Language {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotBlank(message = "Language name is required")
    @Size(max = 100, message = "Language name cannot exceed 100 characters")
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @NotBlank(message = "Language code is required")
    @Size(max = 20, message = "Language code cannot exceed 20 characters")
    @Column(nullable = false, unique = true, length = 20)
    private String code;

}
