package com.watchtower.watchtower.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "runbooks")
public class Runbook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "runbook_tags", joinColumns = @JoinColumn(name = "runbook_id"))
    @Column(name = "tag")
    private List<String> tags = new ArrayList<>();

    /**
     * JSON-encoded embedding vector (e.g. "[0.012,-0.045,...]"), computed
     * once by RunbookSeeder and cached here so the app doesn't re-embed on
     * every restart. Null until indexing has run (e.g. no API key configured
     * yet) - callers must treat that as "not yet searchable", not an error.
     */
    @Column(columnDefinition = "TEXT")
    private String embedding;

    protected Runbook() {
        // JPA
    }

    public Runbook(String title, String content, List<String> tags) {
        this.title = title;
        this.content = content;
        this.tags = tags;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public List<String> getTags() {
        return tags;
    }

    public String getEmbedding() {
        return embedding;
    }

    public void setEmbedding(String embedding) {
        this.embedding = embedding;
    }
}
