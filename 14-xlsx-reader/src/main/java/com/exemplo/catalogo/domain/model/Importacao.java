package com.exemplo.catalogo.domain.model;

import java.time.LocalDateTime;
import java.util.List;

public class Importacao {

    private final Long id;
    private final LocalDateTime dataInicio;
    private final LocalDateTime dataFim;
    private final int totalLinhas;
    private final List<LinhaInvalida> erros;

    public Importacao(Long id, LocalDateTime dataInicio, LocalDateTime dataFim,
                      int totalLinhas, List<LinhaInvalida> erros) {
        this.id = id;
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
        this.totalLinhas = totalLinhas;
        this.erros = List.copyOf(erros);
    }

    public int totalErros() {
        return erros.size();
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

    public List<LinhaInvalida> getErros() {
        return erros;
    }
}
