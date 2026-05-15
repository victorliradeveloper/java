package com.exemplo.catalogo.adapter.out.persistence;

import com.exemplo.catalogo.domain.model.Importacao;
import com.exemplo.catalogo.domain.model.LinhaInvalida;
import com.exemplo.catalogo.domain.port.out.ImportacaoRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
public class ImportacaoRepositoryAdapter implements ImportacaoRepository {

    private final ImportacaoJpaRepository jpa;

    public ImportacaoRepositoryAdapter(ImportacaoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public Importacao salvar(Importacao importacao) {
        List<LinhaInvalidaEntity> errosEntity = importacao.getErros().stream()
                .map(LinhaInvalidaEntity::deDominio)
                .toList();

        ImportacaoEntity entity = new ImportacaoEntity(
                importacao.getDataInicio(),
                importacao.getDataFim(),
                importacao.getTotalLinhas(),
                new java.util.ArrayList<>(errosEntity));

        ImportacaoEntity salvo = jpa.save(entity);

        return new Importacao(
                salvo.getId(),
                salvo.getDataInicio(),
                salvo.getDataFim(),
                salvo.getTotalLinhas(),
                salvo.getErros().stream().map(LinhaInvalidaEntity::paraDominio).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<List<LinhaInvalida>> buscarErrosPorId(Long id) {
        return jpa.findById(id)
                .map(e -> e.getErros().stream()
                        .map(LinhaInvalidaEntity::paraDominio)
                        .toList());
    }
}
