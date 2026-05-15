package com.exemplo.catalogo.adapter.out.persistence;

import com.exemplo.catalogo.domain.model.LinhaInvalida;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "linha_invalida")
public class LinhaInvalidaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int numeroLinha;

    @Column(length = 500)
    private String motivo;

    @Column(length = 2000)
    private String dadosOriginais;

    protected LinhaInvalidaEntity() {
    }

    public LinhaInvalidaEntity(int numeroLinha, String motivo, String dadosOriginais) {
        this.numeroLinha = numeroLinha;
        this.motivo = motivo;
        this.dadosOriginais = dadosOriginais;
    }

    public LinhaInvalida paraDominio() {
        return new LinhaInvalida(numeroLinha, motivo, dadosOriginais);
    }

    public static LinhaInvalidaEntity deDominio(LinhaInvalida linha) {
        return new LinhaInvalidaEntity(
                linha.getNumeroLinha(),
                linha.getMotivo(),
                linha.getDadosOriginais());
    }
}
