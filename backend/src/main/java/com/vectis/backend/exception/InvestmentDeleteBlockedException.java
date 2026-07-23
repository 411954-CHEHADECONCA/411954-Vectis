package com.vectis.backend.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Se lanza al intentar eliminar una inversión que tiene al menos una transacción vinculada
 * en un mes ya cerrado. La eliminación se bloquea por completo (no se revierte nada); la
 * alternativa es "Cobrar" la inversión, que sí está permitida en este caso.
 */
public class InvestmentDeleteBlockedException extends VectisException {

    public InvestmentDeleteBlockedException(UUID id) {
        super("No se puede eliminar la inversión " + id + ": tiene movimientos en un mes ya cerrado. "
                        + "Usá \"Cobrar\" para liquidarla sin revertir el historial.",
                HttpStatus.CONFLICT);
    }
}
