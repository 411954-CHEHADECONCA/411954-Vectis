package com.vectis.backend.domain.entity;

/**
 * Discriminador de origen de una {@link Transaction} vinculada a una inversión — ver
 * {@link Transaction#getInvestmentSourceType()}. Distinto de {@link InvestmentMovementType}:
 * ese enum tipa el movimiento en sí (incluye REVALUO, que nunca genera Transaction), mientras que
 * este tipa la Transaction generada (incluye COLLECTION_CAPITAL/COLLECTION_YIELD, que nunca
 * generan una fila propia en investment_movements).
 */
public enum InvestmentSourceType {
    SUSCRIPCION,
    RESCATE,
    COLLECTION_CAPITAL,
    COLLECTION_YIELD
}
