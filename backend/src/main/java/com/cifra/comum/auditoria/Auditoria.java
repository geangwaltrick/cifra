package com.cifra.comum.auditoria;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra o que aconteceu, sem poder atrapalhar o que aconteceu.
 *
 * <p>Roda em {@code REQUIRES_NEW} por dois motivos. O primeiro: uma tentativa
 * de login recusada precisa ficar registrada, e ela termina em excecao -- se a
 * auditoria compartilhasse a transacao, o rollback apagaria justamente o
 * registro que interessa investigar. O segundo: falha ao auditar nunca deve
 * derrubar a operacao de negocio, entao a excecao e engolida e logada.
 */
@Component
public class Auditoria {

	private static final Log logger = LogFactory.getLog(Auditoria.class);

	private final AuditoriaRepository registros;

	public Auditoria(AuditoriaRepository registros) {
		this.registros = registros;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void registrar(Long atorId, String acao, String recurso, Map<String, Object> payload, String ip) {
		try {
			this.registros.save(new RegistroDeAuditoria(atorId, acao, recurso, payload, ip));
		}
		catch (RuntimeException ex) {
			logger.warn("Falha ao auditar a acao " + acao + ": " + ex.getMessage());
		}
	}

	public void registrar(Long atorId, String acao, String recurso, Map<String, Object> payload) {
		registrar(atorId, acao, recurso, payload, null);
	}

}
