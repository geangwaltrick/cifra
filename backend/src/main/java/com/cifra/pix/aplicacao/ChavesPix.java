package com.cifra.pix.aplicacao;

import java.util.List;
import java.util.UUID;

import com.cifra.comum.ProblemaDeNegocio;
import com.cifra.identidade.dominio.Conta;
import com.cifra.identidade.dominio.Usuario;
import com.cifra.identidade.repositorio.ContaRepository;
import com.cifra.pix.dominio.ChavePix;
import com.cifra.pix.dominio.TipoChavePix;
import com.cifra.pix.repositorio.ChavePixRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cadastro e resolucao de chaves PIX. */
@Service
public class ChavesPix {

	private static final int MAXIMO_POR_CONTA = 5;

	private final ChavePixRepository chaves;

	private final ContaRepository contas;

	public ChavesPix(ChavePixRepository chaves, ContaRepository contas) {
		this.chaves = chaves;
		this.contas = contas;
	}

	@Transactional
	public ChavePix registrar(Conta conta, TipoChavePix tipo, String valorInformado) {
		if (this.chaves.countByContaId(conta.getId()) >= MAXIMO_POR_CONTA) {
			throw ProblemaDeNegocio.requisicaoInvalida("limite-de-chaves-atingido",
					"Cada conta pode ter no maximo %d chaves.".formatted(MAXIMO_POR_CONTA));
		}

		String valor = normalizar(conta.getUsuario(), tipo, valorInformado);

		try {
			return this.chaves.saveAndFlush(new ChavePix(conta.getId(), tipo, valor));
		}
		catch (DataIntegrityViolationException ex) {
			// A unicidade e do banco. Uma chave enderaca uma conta so.
			throw ProblemaDeNegocio.conflito("chave-pix-em-uso",
					"Esta chave ja esta registrada em outra conta.");
		}
	}

	@Transactional(readOnly = true)
	public List<ChavePix> listar(Long contaId) {
		return this.chaves.findByContaIdOrderByCriadoEmAsc(contaId);
	}

	@Transactional
	public void remover(Long contaId, Long chaveId) {
		ChavePix chave = this.chaves.findById(chaveId)
			.orElseThrow(() -> ProblemaDeNegocio.naoEncontrado("chave-pix-nao-encontrada", "Chave nao encontrada."));

		if (!chave.getContaId().equals(contaId)) {
			// Mesma resposta de inexistente: dizer "existe, mas nao e sua"
			// confirmaria a chave de outra pessoa para quem estivesse sondando.
			throw ProblemaDeNegocio.naoEncontrado("chave-pix-nao-encontrada", "Chave nao encontrada.");
		}

		this.chaves.delete(chave);
	}

	@Transactional(readOnly = true)
	public Conta resolver(String valorInformado) {
		String valor = (valorInformado == null) ? "" : valorInformado.trim();

		ChavePix chave = this.chaves.findByValor(valor)
			.or(() -> this.chaves.findByValor(valor.toLowerCase()))
			.or(() -> this.chaves.findByValor(valor.replaceAll("\\D", "")))
			.orElseThrow(() -> ProblemaDeNegocio.naoEncontrado("chave-pix-nao-encontrada",
					"Nenhuma conta encontrada para esta chave."));

		return this.contas.findById(chave.getContaId())
			.orElseThrow(() -> ProblemaDeNegocio.naoEncontrado("conta-nao-encontrada",
					"A conta desta chave nao existe mais."));
	}

	/**
	 * Chave de CPF, e-mail e telefone precisa pertencer de fato ao titular.
	 *
	 * <p>Sem essa amarra, qualquer pessoa registraria o CPF de outra e passaria
	 * a receber o dinheiro endereçado a ela.
	 */
	private String normalizar(Usuario titular, TipoChavePix tipo, String valorInformado) {
		String valor = (valorInformado == null) ? "" : valorInformado.trim();

		return switch (tipo) {
			case ALEATORIA -> UUID.randomUUID().toString();

			case CPF -> {
				String digitos = valor.replaceAll("\\D", "");
				if (!digitos.equals(titular.getCpf().valor())) {
					throw ProblemaDeNegocio.requisicaoInvalida("chave-pix-nao-pertence-ao-titular",
							"A chave de CPF deve ser o CPF do titular da conta.");
				}
				yield digitos;
			}

			case EMAIL -> {
				String email = valor.toLowerCase();
				if (!email.equals(titular.getEmail())) {
					throw ProblemaDeNegocio.requisicaoInvalida("chave-pix-nao-pertence-ao-titular",
							"A chave de e-mail deve ser o e-mail cadastrado do titular.");
				}
				yield email;
			}

			case TELEFONE -> {
				String digitos = valor.replaceAll("\\D", "");
				if (digitos.length() < 10 || digitos.length() > 11) {
					throw ProblemaDeNegocio.requisicaoInvalida("telefone-invalido",
							"Informe o telefone com DDD, apenas digitos.");
				}
				yield digitos;
			}
		};
	}

}
