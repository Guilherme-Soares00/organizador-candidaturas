package br.com.portfolio.candidaturas;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Set;

@Component
public class LocalOriginFilter extends OncePerRequestFilter {
    private final ObjectMapper json;

    public LocalOriginFilter(ObjectMapper json) {
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        boolean localHost = "localhost".equalsIgnoreCase(request.getServerName()) || "127.0.0.1".equals(request.getServerName());
        boolean mutation = !Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
        if (!localHost || (request.getRequestURI().startsWith("/api/") && mutation &&
            ("cross-site".equals(request.getHeader("Sec-Fetch-Site")) || !sameOrigin(request)))) {
            response.setStatus(403);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            json.writeValue(response.getWriter(), new ApiExceptionHandler.Erro("Origem não permitida", List.of("Abra a aplicação no endereço local do servidor")));
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean sameOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null) return true;
        try {
            URI uri = URI.create(origin);
            int port = uri.getPort() == -1 ? ("https".equals(uri.getScheme()) ? 443 : 80) : uri.getPort();
            return request.getScheme().equals(uri.getScheme()) && request.getServerName().equalsIgnoreCase(uri.getHost())
                && request.getServerPort() == port && uri.getUserInfo() == null && (uri.getRawPath() == null || uri.getRawPath().isEmpty())
                && uri.getRawQuery() == null && uri.getRawFragment() == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
