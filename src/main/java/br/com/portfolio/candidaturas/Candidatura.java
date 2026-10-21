package br.com.portfolio.candidaturas;

import java.time.LocalDate;

public record Candidatura(Long id, String empresa, String cargo, String link, LocalDate dataCandidatura, Etapa etapa, String observacoes) {}
