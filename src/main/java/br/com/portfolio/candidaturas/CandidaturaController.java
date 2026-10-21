package br.com.portfolio.candidaturas;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api/candidaturas")
public class CandidaturaController {
    private final CandidaturaRepository repository;

    public CandidaturaController(CandidaturaRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public java.util.List<Candidatura> list(@RequestParam(required = false) Etapa etapa, @RequestParam(defaultValue = "") String busca) {
        String term = busca.strip().toLowerCase(java.util.Locale.ROOT);
        return repository.list().stream().filter(item -> etapa == null || item.etapa() == etapa)
            .filter(item -> item.empresa().toLowerCase(java.util.Locale.ROOT).contains(term) || item.cargo().toLowerCase(java.util.Locale.ROOT).contains(term)).toList();
    }

    @GetMapping("/{id}")
    public Candidatura find(@PathVariable long id) {
        return repository.find(id);
    }

    @PutMapping("/{id}")
    public Candidatura update(@PathVariable long id, @jakarta.validation.Valid @RequestBody CandidaturaInput input) {
        return repository.update(id, input.toCandidatura());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        repository.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<Candidatura> create(@jakarta.validation.Valid @RequestBody CandidaturaInput input) {
        Candidatura item = repository.create(input.toCandidatura());
        return ResponseEntity.created(URI.create("/api/candidaturas/" + item.id())).body(item);
    }
}
