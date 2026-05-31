package com.exemplo.catalogo.adapter.out.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "importacao")
public class ImportacaoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime dataInicio;
    private LocalDateTime dataFim;
    private int totalLinhas;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "importacao_id")
    private List<LinhaInvalidaEntity> erros = new ArrayList<>();

    protected ImportacaoEntity() {
    }

    public ImportacaoEntity(LocalDateTime dataInicio, LocalDateTime dataFim,
                            int totalLinhas, List<LinhaInvalidaEntity> erros) {
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
        this.totalLinhas = totalLinhas;
        this.erros = erros;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getDataInicio() {
        return dataInicio;
    }

    public LocalDateTime getDataFim() {
        return dataFim;
    }

    public int getTotalLinhas() {
        return totalLinhas;
    }

    public List<LinhaInvalidaEntity> getErros() {
        return erros;
    }
}
