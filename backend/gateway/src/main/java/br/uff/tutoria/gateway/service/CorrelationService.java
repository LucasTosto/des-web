package br.uff.tutoria.gateway.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CorrelationService {

    private final Map<String, String> correlations =
            new ConcurrentHashMap<>();

    public void associar(String correlationId, String clientId) {
        correlations.put(correlationId, clientId);
    }

    public String buscarCliente(String correlationId) {
        return correlations.remove(correlationId);
    }

    public void remover(String correlationId) {
        correlations.remove(correlationId);
    }
}
