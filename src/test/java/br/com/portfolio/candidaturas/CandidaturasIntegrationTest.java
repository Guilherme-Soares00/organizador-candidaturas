package br.com.portfolio.candidaturas;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class CandidaturasIntegrationTest {
    static final Path DATABASE = database();
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    static Path database() {
        try {
            return Files.createTempDirectory(Files.createDirectories(Path.of("target", "test-data")), "candidaturas-").resolve("isolado.db");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE);
    }

    String payload(String empresa, String cargo, String etapa) throws Exception {
        return json.writeValueAsString(java.util.Map.of("empresa", empresa, "cargo", cargo, "link", "https://example.com/vaga", "dataCandidatura", "2025-01-10", "etapa", etapa, "observacoes", "Contato inicial"));
    }

    @BeforeEach
    void limpaBanco() {
        jdbc.update("DELETE FROM candidaturas");
    }

    long cria(String empresa, String cargo, String etapa) throws Exception {
        return json.readTree(mvc.perform(post("/api/candidaturas").contentType("application/json").content(payload(empresa, cargo, etapa)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void listaEBuscaCandidaturasPersistidas() throws Exception {
        long id = cria("Acme", "Java Jr", "ENVIADA");
        mvc.perform(get("/api/candidaturas")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/candidaturas/{id}", id)).andExpect(status().isOk()).andExpect(jsonPath("$.empresa").value("Acme"));
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + DATABASE);
             var statement = connection.createStatement(); var result = statement.executeQuery("SELECT empresa FROM candidaturas")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("Acme");
        }
    }

    @Test
    void atualizaEExcluiCandidatura() throws Exception {
        long id = cria("Acme", "Java Jr", "ENVIADA");
        mvc.perform(put("/api/candidaturas/{id}", id).contentType("application/json").content(payload("Outra", "Backend", "ENTREVISTA")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.etapa").value("ENTREVISTA"));
        mvc.perform(get("/api/candidaturas/{id}", id)).andExpect(jsonPath("$.empresa").value("Outra"));
        mvc.perform(delete("/api/candidaturas/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/candidaturas/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/candidaturas/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(put("/api/candidaturas/{id}", id).contentType("application/json").content(payload("Outra", "Backend", "ENVIADA"))).andExpect(status().isNotFound());
    }

    @Test
    void filtraPorEtapaEBuscaSemDiferenciarMaiusculas() throws Exception {
        cria("Acme", "Java Jr", "ENVIADA");
        cria("Beta", "Java Pleno", "ENTREVISTA");
        cria("Gamma", "Designer", "ENVIADA");
        mvc.perform(get("/api/candidaturas").param("busca", "aCmE")).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/candidaturas").param("busca", "jAvA").param("etapa", "ENVIADA"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].empresa").value("Acme"));
        mvc.perform(get("/api/candidaturas").param("busca", "inexistente")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void resumoSempreIncluiTodasAsEtapasEIgnoraFiltros() throws Exception {
        mvc.perform(get("/api/resumo")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.porEtapa.ENVIADA").value(0)).andExpect(jsonPath("$.porEtapa.ENTREVISTA").value(0))
            .andExpect(jsonPath("$.porEtapa.PROPOSTA").value(0)).andExpect(jsonPath("$.porEtapa.ENCERRADA").value(0));
        cria("Acme", "Java", "ENVIADA");
        cria("Beta", "Design", "ENTREVISTA");
        mvc.perform(get("/api/resumo").param("etapa", "PROPOSTA").param("busca", "Nada"))
            .andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.porEtapa.ENVIADA").value(1))
            .andExpect(jsonPath("$.porEtapa.ENTREVISTA").value(1));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"empresa, ", "cargo, ", "empresa,LONG150", "cargo,LONG150", "link,LONG1000", "observacoes,LONG5000", "link,javascript:alert(1)", "link,ftp://example.com", "link,http://", "link,https://example.com/a b", "dataCandidatura,2999-01-01", "dataCandidatura,2025-02-30", "dataCandidatura,invalida", "etapa,INVALIDA", "etapa,"})
    void rejeitaCamposInvalidos(String campo, String valor) throws Exception {
        var input = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(payload("Acme", "Java", "ENVIADA"));
        if (valor != null && valor.startsWith("LONG")) valor = "a".repeat(Integer.parseInt(valor.substring(4)) + 1);
        input.put(campo, valor == null ? "" : valor);
        mvc.perform(post("/api/candidaturas").contentType("application/json").content(input.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensagem").isString()).andExpect(jsonPath("$.erros").isArray());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM candidaturas", Integer.class)).isZero();
    }

    @Test
    void padronizaErrosDeJsonParametrosERecursoAusente() throws Exception {
        mvc.perform(post("/api/candidaturas").contentType("application/json").content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erros").isArray());
        mvc.perform(post("/api/candidaturas").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erros").isArray());
        mvc.perform(get("/api/candidaturas").param("etapa", "invalida"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensagem").isString());
        mvc.perform(get("/api/candidaturas/nao-numero")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.erros").isArray());
        mvc.perform(get("/api/candidaturas/999999")).andExpect(status().isNotFound()).andExpect(jsonPath("$.mensagem").value("Candidatura não encontrada"));
    }

    @Test
    void bloqueiaMutacoesDeOutraOrigemSemCors() throws Exception {
        long id = cria("Acme", "Java", "ENVIADA");
        mvc.perform(post("/api/candidaturas").header("Origin", "https://evil.example").contentType("application/json").content(payload("Outra", "Java", "ENVIADA")))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.mensagem").isString()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mvc.perform(put("/api/candidaturas/{id}", id).header("Origin", "null").contentType("application/json").content(payload("Outra", "Java", "ENVIADA"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/candidaturas/{id}", id).header("Sec-Fetch-Site", "cross-site")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/candidaturas/{id}", id).header("Origin", "http://localhost:9999")).andExpect(status().isForbidden());
        mvc.perform(post("/api/candidaturas").header("Origin", "http://localhost").contentType("application/json").content(payload("Mesma origem", "Java", "ENVIADA"))).andExpect(status().isCreated());
        mvc.perform(get("/api/candidaturas/{id}", id)).andExpect(status().isOk());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"/api/candidaturas", "/api/candidaturas/1", "/api/resumo", "/", "/app.mjs"})
    void bloqueiaLeiturasComHostDeRebindingMesmoComOrigemCorrespondente(String rota) throws Exception {
        cria("Dados privados", "Java", "ENVIADA");
        mvc.perform(get(java.net.URI.create("http://evil.example" + rota)).header("Host", "evil.example")
                .header("Origin", "http://evil.example").header("Sec-Fetch-Site", "same-origin"))
            .andExpect(status().isForbidden()).andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(content().json("{\"mensagem\":\"Origem não permitida\",\"erros\":[\"Abra a aplicação no endereço local do servidor\"]}"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"evil.example", "localhost.evil.example", "127.0.0.2"})
    void bloqueiaCriacaoComHostNaoPermitidoMesmoComOrigemCorrespondente(String host) throws Exception {
        mvc.perform(post(java.net.URI.create("http://" + host + "/api/candidaturas")).header("Host", host)
                .header("Origin", "http://" + host).header("Sec-Fetch-Site", "same-origin")
                .contentType("application/json").content(payload("Invasor", "Java", "ENVIADA")))
            .andExpect(status().isForbidden()).andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(content().json("{\"mensagem\":\"Origem não permitida\",\"erros\":[\"Abra a aplicação no endereço local do servidor\"]}"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM candidaturas", Integer.class)).isZero();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"GET", "HEAD", "OPTIONS", "POST", "PUT", "PATCH", "DELETE"})
    void bloqueiaHostNaoPermitidoEmQualquerMetodoSemOrigin(String metodo) throws Exception {
        mvc.perform(request(org.springframework.http.HttpMethod.valueOf(metodo), java.net.URI.create("http://evil.example/app.mjs"))
                .header("Host", "evil.example"))
            .andExpect(status().isForbidden()).andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.mensagem").value("Origem não permitida")).andExpect(jsonPath("$.erros").isArray());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"localhost", "LOCALHOST", "127.0.0.1"})
    void permiteHostsLocaisComOrigemCorrespondente(String host) throws Exception {
        String base = "http://" + host + ":8080";
        mvc.perform(post(java.net.URI.create(base + "/api/candidaturas")).header("Host", host + ":8080")
                .header("Origin", base).header("Sec-Fetch-Site", "same-origin")
                .contentType("application/json").content(payload("Local", "Java", "ENVIADA")))
            .andExpect(status().isCreated());
        mvc.perform(get(java.net.URI.create(base + "/api/candidaturas")).header("Host", host + ":8080"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].empresa").value("Local"));
        mvc.perform(get(java.net.URI.create(base + "/app.mjs")).header("Host", host + ":8080"))
            .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/javascript"));
    }

    @Test
    void aceitaOpcionaisAusentesEDatadeHoje() throws Exception {
        var input = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(payload("Acme", "Java", "PROPOSTA"));
        input.remove("link");
        input.remove("observacoes");
        input.put("dataCandidatura", java.time.LocalDate.now().toString());
        mvc.perform(post("/api/candidaturas").contentType("application/json").content(input.toString()))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.link").isEmpty()).andExpect(jsonPath("$.observacoes").isEmpty());
        input.put("link", "");
        input.put("observacoes", "");
        mvc.perform(post("/api/candidaturas").contentType("application/json").content(input.toString())).andExpect(status().isCreated());
    }

    @Test
    void trataSqlComoTextoEBuscaLiteral() throws Exception {
        String attack = "Robert'); DROP TABLE candidaturas;--";
        long id = cria(attack, "Analista 100%_", "ENCERRADA");
        mvc.perform(get("/api/candidaturas/{id}", id)).andExpect(jsonPath("$.empresa").value(attack));
        mvc.perform(get("/api/candidaturas").param("busca", "' OR 1=1 --")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/candidaturas").param("busca", "%_")).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(put("/api/candidaturas/{id}", id).contentType("application/json").content(payload(attack, "Outro", "PROPOSTA"))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM candidaturas", Integer.class)).isEqualTo(1);
    }

    @Test
    void inicializacaoDoRepositorioPreservaDados() throws Exception {
        long id = cria("Persistente", "Java", "ENVIADA");
        var reopened = new CandidaturaRepository(jdbc, "jdbc:sqlite:" + DATABASE);
        assertThat(reopened.find(id).empresa()).isEqualTo("Persistente");
        Path nested = DATABASE.getParent().resolve("novo/sub/banco.db");
        var source = new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:sqlite:" + nested);
        var isolated = new CandidaturaRepository(new JdbcTemplate(source), "jdbc:sqlite:" + nested);
        assertThat(Files.exists(nested)).isTrue();
        assertThat(isolated.list()).isEmpty();
    }

    @Test
    void validacaoTambemProtegeAtualizacao() throws Exception {
        long id = cria("Acme", "Java", "ENVIADA");
        mvc.perform(put("/api/candidaturas/{id}", id).contentType("application/json").content(payload(" ", "Java", "ENVIADA")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erros").isArray());
        mvc.perform(get("/api/candidaturas/{id}", id)).andExpect(jsonPath("$.empresa").value("Acme"));
    }

    @Test
    void serveModulosJavascriptComMimeCorreto() throws Exception {
        mvc.perform(get("/app.mjs")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/javascript"));
        mvc.perform(get("/helpers.mjs")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/javascript"));
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
    }

    @Test
    void criaCandidaturaComTodosOsCampos() throws Exception {
        mvc.perform(post("/api/candidaturas").contentType("application/json").content(payload("Acme", "Java Jr", "ENVIADA")))
            .andExpect(status().isCreated()).andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.id").isNumber()).andExpect(jsonPath("$.empresa").value("Acme"))
            .andExpect(jsonPath("$.cargo").value("Java Jr")).andExpect(jsonPath("$.link").value("https://example.com/vaga"))
            .andExpect(jsonPath("$.dataCandidatura").value("2025-01-10"))
            .andExpect(jsonPath("$.etapa").value("ENVIADA")).andExpect(jsonPath("$.observacoes").value("Contato inicial"));
    }
}
