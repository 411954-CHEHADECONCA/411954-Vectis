package com.vectis.backend.service;

import com.vectis.backend.config.PpiMarketDataClient;
import com.vectis.backend.domain.entity.DoctaInstrumentCache;
import com.vectis.backend.repository.DoctaInstrumentCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Mantiene el catálogo de instrumentos (docta_instrument_cache) al día contra PPI, para que no se
 * desactualice a mano (instrumentos que vencen o cambian de ticker, p. ej. S18D6).
 *
 * <p>Dos pasos, resilientes (nunca lanzan hacia el scheduler):
 * <ol>
 *   <li><b>Pruning/validación</b>: los instrumentos vigentes de tipo LETRA/BONO/ON que PPI ya no
 *       reconoce se marcan {@code active=false} (se ocultan del selector, no se borran).</li>
 *   <li><b>Descubrimiento de LECAPs</b>: se buscan las letras/bonos capitalizables en pesos y se
 *       hace upsert por ticker, con el vencimiento parseado de la descripción.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstrumentCatalogSyncService {

    /** Alcance del sync automático: sólo LETRA (LECAPs). BONO/ON quedan curados (validados en V039). */
    private static final String MANAGED_TYPE = "LETRA";
    private static final int NOMBRE_MAX_LEN = 255;

    /** Vencimiento embebido en la descripción de PPI, p. ej. "... CAPITALIZABLE 30/11/26 $". */
    private static final Pattern MATURITY_PATTERN = Pattern.compile("\\d{2}/\\d{2}/\\d{2}");
    private static final DateTimeFormatter MATURITY_FMT = DateTimeFormatter.ofPattern("dd/MM/yy");

    private final PpiMarketDataClient            ppiMarketDataClient;
    private final DoctaInstrumentCacheRepository instrumentCacheRepository;

    /** Evita solapamiento entre el sync de arranque (ApplicationReadyEvent) y el @Scheduled semanal. */
    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);

    // ─── Disparadores ───────────────────────────────────────────────────────────

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("Sincronizando catálogo de instrumentos con PPI al arranque...");
        syncCatalog();
    }

    /** Semanal — lunes 06:00 UTC. */
    @Scheduled(cron = "0 0 6 * * MON")
    public void scheduledSync() {
        syncCatalog();
    }

    // ─── Sync ────────────────────────────────────────────────────────────────────

    /**
     * Sincroniza las LECAPs del catálogo contra PPI en <b>una sola llamada</b> de descubrimiento, que
     * actúa como fuente de verdad: los tickers LETRA del catálogo que ya no aparecen se dan de baja
     * (active=false) y los descubiertos se hacen upsert. Si PPI no devuelve nada (no configurado o
     * fallo transitorio), <b>no se da de baja nada</b> para no desactivar instrumentos válidos por un
     * error de red.
     *
     * <p>Decisión de diseño: no es {@code @Transactional} (auto-invocación desde los disparadores +
     * cada {@code save} corre en su propia transacción, con resiliencia por ítem). Si se necesitara
     * una transacción envolvente, el patrón canónico es extraer la lógica a un bean secundario e
     * invocarlo por el proxy. Un {@link AtomicBoolean} evita el solapamiento arranque/cron.
     */
    public void syncCatalog() {
        if (!ppiMarketDataClient.isConfigured()) {
            log.info("PPI no configurado — se omite syncCatalog");
            return;
        }
        if (!syncInProgress.compareAndSet(false, true)) {
            log.info("syncCatalog ya en curso — se omite esta ejecución");
            return;
        }
        try {
            // 1) Descubrir las LECAPs vigentes en pesos (una sola llamada a PPI).
            List<PpiMarketDataClient.PpiInstrument> lecaps =
                    ppiMarketDataClient.searchInstruments("LETRA TESORO", "").stream()
                            .filter(this::isPesoLecap)
                            .toList();

            if (lecaps.isEmpty()) {
                log.warn("PPI no devolvió LECAPs — se omite el sync (no se da de baja nada) para evitar "
                        + "desactivar instrumentos válidos ante un fallo transitorio");
                return;
            }

            Set<String> vigentes = lecaps.stream()
                    .map(i -> i.ticker().toUpperCase())
                    .collect(Collectors.toSet());

            int deactivated = pruneMissing(vigentes);
            int upserted    = 0;
            for (PpiMarketDataClient.PpiInstrument dto : lecaps) {
                try {
                    if (upsertLetra(dto)) upserted++;
                } catch (Exception e) {
                    log.warn("No se pudo upsertear instrumento {}: {}", dto.ticker(), e.getMessage());
                }
            }
            log.info("Sync catálogo PPI: {} dados de baja, {} descubiertos/actualizados", deactivated, upserted);
        } finally {
            syncInProgress.set(false);
        }
    }

    /**
     * Da de baja (active=false) las LETRA del catálogo cuyo ticker no está en el set vigente de PPI.
     * No borra: los activos que referencian el ticker siguen intactos. Sólo se ejecuta cuando el
     * descubrimiento devolvió datos (PPI reachable), así que no hay falsos positivos por error de red.
     */
    private int pruneMissing(Set<String> vigentes) {
        int deactivated = 0;
        for (DoctaInstrumentCache inst : instrumentCacheRepository.findAllByTipoInAndActiveTrue(List.of(MANAGED_TYPE))) {
            try {
                if (!vigentes.contains(inst.getTicker().toUpperCase())) {
                    inst.setActive(false);
                    instrumentCacheRepository.save(inst);
                    deactivated++;
                    log.debug("LECAP dada de baja (ya no cotiza en PPI): {}", inst.getTicker());
                }
            } catch (Exception e) {
                log.warn("No se pudo dar de baja el instrumento {}: {}", inst.getTicker(), e.getMessage());
            }
        }
        return deactivated;
    }

    /** LECAP en pesos: tipo PPI LETRAS + descripción capitalizable + moneda pesos. */
    private boolean isPesoLecap(PpiMarketDataClient.PpiInstrument dto) {
        String type = dto.type() == null ? "" : dto.type().toUpperCase();
        String desc = dto.description() == null ? "" : dto.description().toUpperCase();
        String ccy  = dto.currency() == null ? "" : dto.currency().toUpperCase();
        return type.equals("LETRAS")
                && desc.contains("CAPITALIZABLE")
                && ccy.contains("PESO")
                && dto.ticker() != null && !dto.ticker().isBlank();
    }

    /** Crea o actualiza la entrada del catálogo; retorna true si hubo alta o cambio real. */
    private boolean upsertLetra(PpiMarketDataClient.PpiInstrument dto) {
        LocalDate maturity = parseMaturity(dto.description());
        String nombre = truncate(dto.description());
        Optional<DoctaInstrumentCache> existing = instrumentCacheRepository.findByTicker(dto.ticker());

        if (existing.isPresent()) {
            DoctaInstrumentCache inst = existing.get();
            boolean changed = !inst.isActive()
                    || !nombre.equals(inst.getNombre())
                    || (maturity != null && !maturity.equals(inst.getMaturityDate()));
            if (changed) {
                inst.setActive(true);
                inst.setNombre(nombre);
                inst.setTipo(MANAGED_TYPE);
                if (maturity != null) inst.setMaturityDate(maturity);
                instrumentCacheRepository.save(inst);
            }
            return changed;
        }

        instrumentCacheRepository.save(DoctaInstrumentCache.builder()
                .ticker(dto.ticker())
                .nombre(nombre)
                .tipo(MANAGED_TYPE)
                .maturityDate(maturity)
                .active(true)
                .build());
        return true;
    }

    /** Trunca el nombre al máximo de la columna para no fallar el save ante descripciones largas. */
    private static String truncate(String description) {
        if (description == null) return "";
        return description.length() <= NOMBRE_MAX_LEN ? description : description.substring(0, NOMBRE_MAX_LEN);
    }

    /** Extrae el primer "DD/MM/YY" de la descripción como fecha, o null si no hay/está mal formada. */
    static LocalDate parseMaturity(String description) {
        if (description == null) return null;
        Matcher m = MATURITY_PATTERN.matcher(description);
        if (!m.find()) return null;
        try {
            return LocalDate.parse(m.group(0), MATURITY_FMT);
        } catch (Exception e) {
            return null;
        }
    }
}
