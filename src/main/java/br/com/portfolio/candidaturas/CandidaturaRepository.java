package br.com.portfolio.candidaturas;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.nio.file.Files;
import java.nio.file.Path;

@Repository
public class CandidaturaRepository {
    private final JdbcTemplate jdbc;

    public CandidaturaRepository(JdbcTemplate jdbc, @Value("${spring.datasource.url}") String url) throws java.io.IOException {
        this.jdbc = jdbc;
        Path parent = Path.of(url.substring("jdbc:sqlite:".length())).toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        jdbc.execute("CREATE TABLE IF NOT EXISTS candidaturas (id INTEGER PRIMARY KEY AUTOINCREMENT, empresa TEXT NOT NULL, cargo TEXT NOT NULL, link TEXT, data_candidatura TEXT NOT NULL, etapa TEXT NOT NULL, observacoes TEXT)");
    }

    private final org.springframework.jdbc.core.RowMapper<Candidatura> mapper = (rs, row) -> new Candidatura(rs.getLong("id"), rs.getString("empresa"), rs.getString("cargo"), rs.getString("link"), java.time.LocalDate.parse(rs.getString("data_candidatura")), Etapa.valueOf(rs.getString("etapa")), rs.getString("observacoes"));

    public java.util.List<Candidatura> list() {
        return jdbc.query("SELECT * FROM candidaturas ORDER BY data_candidatura DESC, id DESC", mapper);
    }

    public Candidatura find(long id) {
        return jdbc.query("SELECT * FROM candidaturas WHERE id = ?", mapper, id).stream().findFirst().orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Candidatura não encontrada"));
    }

    @org.springframework.transaction.annotation.Transactional
    public Candidatura update(long id, Candidatura item) {
        if (jdbc.update("UPDATE candidaturas SET empresa=?,cargo=?,link=?,data_candidatura=?,etapa=?,observacoes=? WHERE id=?", item.empresa(), item.cargo(), item.link(), item.dataCandidatura().toString(), item.etapa().name(), item.observacoes(), id) == 0) find(id);
        return find(id);
    }

    public void delete(long id) {
        if (jdbc.update("DELETE FROM candidaturas WHERE id=?", id) == 0) find(id);
    }

    @org.springframework.transaction.annotation.Transactional
    public Candidatura create(Candidatura item) {
        jdbc.update("INSERT INTO candidaturas (empresa,cargo,link,data_candidatura,etapa,observacoes) VALUES (?,?,?,?,?,?)", item.empresa(), item.cargo(), item.link(), item.dataCandidatura().toString(), item.etapa().name(), item.observacoes());
        Long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
        return new Candidatura(id, item.empresa(), item.cargo(), item.link(), item.dataCandidatura(), item.etapa(), item.observacoes());
    }
}
