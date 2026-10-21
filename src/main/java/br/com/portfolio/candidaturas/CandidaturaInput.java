package br.com.portfolio.candidaturas;

import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.net.URI;
import java.time.LocalDate;

public record CandidaturaInput(
    @NotBlank(message = "Informe a empresa") @Size(max = 150, message = "Empresa deve ter no máximo 150 caracteres") String empresa,
    @NotBlank(message = "Informe o cargo") @Size(max = 150, message = "Cargo deve ter no máximo 150 caracteres") String cargo,
    @Size(max = 1000, message = "Link deve ter no máximo 1000 caracteres") String link,
    @NotNull(message = "Informe a data da candidatura") @PastOrPresent(message = "Data da candidatura não pode estar no futuro") LocalDate dataCandidatura,
    @NotNull(message = "Informe uma etapa válida") Etapa etapa,
    @Size(max = 5000, message = "Observações devem ter no máximo 5000 caracteres") String observacoes
) {
    @JsonIgnore
    @AssertTrue(message = "Link deve ser uma URL HTTP ou HTTPS válida")
    public boolean isLinkValido() {
        if (link == null || link.isBlank()) return true;
        try {
            URI uri = URI.create(link);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public Candidatura toCandidatura() {
        return new Candidatura(null, empresa.strip(), cargo.strip(), link == null || link.isBlank() ? null : link, dataCandidatura, etapa, observacoes);
    }
}
