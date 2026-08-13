package com.cifra.identidade.aplicacao;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import com.cifra.comum.ProblemaDeNegocio;
import com.cifra.configuracao.PropriedadesDoCifra;

import org.springframework.stereotype.Component;

/**
 * Freio de forca bruta no login: N tentativas por janela, por chave.
 *
 * <p>Limitacao assumida: o estado vive na memoria desta instancia. Serve a uma
 * demo de instancia unica. Com mais de um no, isto vira Redis -- e a interface
 * publica nao muda quando esse dia chegar.
 */
@Component
public class LimitadorDeTentativas {

	private final ConcurrentHashMap<String, Janela> janelas = new ConcurrentHashMap<>();

	private final int limite;

	private final Duration duracaoDaJanela;

	public LimitadorDeTentativas(PropriedadesDoCifra propriedades) {
		this.limite = propriedades.login().tentativasPorJanela();
		this.duracaoDaJanela = propriedades.login().janela();
	}

	/** Conta a tentativa e rejeita quando a janela estoura. */
	public void registrarTentativa(String chave) {
		Instant agora = Instant.now();

		Janela janela = this.janelas.compute(chave, (ignorada, atual) -> {
			if (atual == null || agora.isAfter(atual.reiniciaEm())) {
				return new Janela(1, agora.plus(this.duracaoDaJanela));
			}
			return new Janela(atual.tentativas() + 1, atual.reiniciaEm());
		});

		if (janela.tentativas() > this.limite) {
			long segundos = Duration.between(agora, janela.reiniciaEm()).toSeconds();
			throw ProblemaDeNegocio
				.excessoDeTentativas("Tentativas demais. Tente de novo em %d segundos.".formatted(Math.max(segundos, 1)));
		}
	}

	/** Zera a contagem apos autenticacao bem-sucedida. */
	public void liberar(String chave) {
		this.janelas.remove(chave);
	}

	private record Janela(int tentativas, Instant reiniciaEm) {
	}

}
