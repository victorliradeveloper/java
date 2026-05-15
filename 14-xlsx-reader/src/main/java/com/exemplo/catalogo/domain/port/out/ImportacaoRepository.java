package com.exemplo.catalogo.domain.port.out;

import com.exemplo.catalogo.domain.model.Importacao;
import com.exemplo.catalogo.domain.model.LinhaInvalida;

import java.util.List;
import java.util.Optional;

public interface ImportacaoRepository {

    Importacao salvar(Importacao importacao);

    Optional<List<LinhaInvalida>> buscarErrosPorId(Long id);
}
