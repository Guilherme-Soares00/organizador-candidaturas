package br.com.portfolio.candidaturas;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.EnumMap;
import java.util.Map;

@RestController
public class ResumoController {
    private final CandidaturaRepository repository;

    public ResumoController(CandidaturaRepository repository) {
        this.repository = repository;
    }

    public record Resumo(long total, Map<Etapa, Long> porEtapa) {}

    @GetMapping("/api/resumo")
    public Resumo resumo() {
        var items = repository.list();
        Map<Etapa, Long> counts = new EnumMap<>(Etapa.class);
        for (Etapa etapa : Etapa.values()) counts.put(etapa, 0L);
        for (Candidatura item : items) counts.merge(item.etapa(), 1L, Long::sum);
        return new Resumo(items.size(), counts);
    }
}
