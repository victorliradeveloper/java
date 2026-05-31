package com.microservices.notification.infrastructure.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

// Baseline declarativo: marca o ponto zero do versionamento de schema.
// processed_messages soh tem indice em _id (implicito do Mongo) e eh criada
// on-demand no primeiro insert. Nada a criar/migrar, mas a entry em
// mongockChangeLog estabelece o V001 explicito - proximas migracoes serao V002+.
@ChangeUnit(id = "V001_baseline", order = "001", author = "victor")
public class V001_Baseline {

    @Execution
    public void execution() {
        // Intencional: nenhum estado a migrar. Ver comentario da classe.
    }

    @RollbackExecution
    public void rollback() {
        // Intencional: no-op. Nada foi criado, nada a desfazer.
    }
}
