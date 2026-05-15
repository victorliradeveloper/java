package com.exemplo.catalogo.adapter.in.rest.dto;

import com.exemplo.catalogo.domain.model.Importacao;

import java.time.LocalDateTime;

public record ImportacaoResponse(Long id,
                                 LocalDateTime dataInicio,
                                 LocalDateTime dataFim,
                                 int totalLinhas,
                                 int totalErros) {

    public static ImportacaoResponse from(Importacao importacao) {
        return new ImportacaoResponse(
                importacao.getId(),
                importacao.getDataInicio(),
                importacao.getDataFim(),
                importacao.getTotalLinhas(),
                importacao.totalErros());
    }
}
