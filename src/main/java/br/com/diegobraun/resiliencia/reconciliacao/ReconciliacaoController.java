package br.com.diegobraun.resiliencia.reconciliacao;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reconciliacao")
public class ReconciliacaoController {

    private final ReconciliacaoService service;

    public ReconciliacaoController(ReconciliacaoService service) {
        this.service = service;
    }

    @PostMapping
    public RelatorioReconciliacao executar(@RequestParam(required = false) Long idadeMinimaMs) {
        return idadeMinimaMs != null ? service.executar(idadeMinimaMs) : service.executarPadrao();
    }

    @GetMapping("/ultima")
    public ResponseEntity<RelatorioReconciliacao> ultima() {
        RelatorioReconciliacao ultimo = service.ultimo();
        return ultimo == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(ultimo);
    }
}
