package com.exemplo.catalogo.domain.model;

public class LinhaInvalida {

    private final int numeroLinha;
    private final String motivo;
    private final String dadosOriginais;

    public LinhaInvalida(int numeroLinha, String motivo, String dadosOriginais) {
        this.numeroLinha = numeroLinha;
        this.motivo = motivo;
        this.dadosOriginais = dadosOriginais;
    }

    public int getNumeroLinha() {
        return numeroLinha;
    }

    public String getMotivo() {
        return motivo;
    }

    public String getDadosOriginais() {
        return dadosOriginais;
    }
}
