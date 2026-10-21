package br.com.portfolio.candidaturas;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {
    public record Erro(String mensagem, List<String> erros) {}

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Erro> validation(MethodArgumentNotValidException exception) {
        var erros = exception.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage()).sorted().toList();
        return ResponseEntity.badRequest().body(new Erro("Revise os campos informados", erros));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Erro> unreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(new Erro("Dados inválidos", List.of("Envie JSON válido, data no formato AAAA-MM-DD e etapa ENVIADA, ENTREVISTA, PROPOSTA ou ENCERRADA")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Erro> parameter(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body(new Erro("Parâmetro inválido", List.of(exception.getName() + ": valor inválido")));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Erro> missing(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(new Erro(exception.getReason(), List.of()));
    }
}
