package com.cifra.identidade.aplicacao;

import com.cifra.comum.ProblemaDeNegocio;
import com.cifra.identidade.dominio.Usuario;
import com.cifra.identidade.repositorio.UsuarioRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Segunda senha, exigida apenas para tirar dinheiro da conta.
 *
 * <p>Serve a um cenario concreto: token de acesso vazado nao basta para
 * esvaziar a conta. Ler saldo e extrato continua so com o token; mover dinheiro
 * exige algo que o atacante nao capturou junto.
 *
 * <p>Enquanto o titular nao define uma, as movimentacoes seguem apenas com o
 * token -- a protecao e opcional por escolha dele, nao um obstaculo imposto no
 * cadastro.
 */
@Service
public class SenhaTransacional {

	private final UsuarioRepository usuarios;

	private final PasswordEncoder senhas;

	public SenhaTransacional(UsuarioRepository usuarios, PasswordEncoder senhas) {
		this.usuarios = usuarios;
		this.senhas = senhas;
	}

	@Transactional
	public void definir(Long usuarioId, String senhaDeAcesso, String novaSenhaTransacional) {
		Usuario usuario = carregar(usuarioId);

		// Trocar a senha de movimentacao exige provar a de acesso: sem isso,
		// um token vazado bastaria para definir a segunda senha e contornar
		// justamente a protecao que ela existe para dar.
		if (!this.senhas.matches(senhaDeAcesso, usuario.getSenhaHash())) {
			throw ProblemaDeNegocio.naoAutorizado("credenciais-invalidas", "Senha de acesso incorreta.");
		}
		if (novaSenhaTransacional.equals(senhaDeAcesso)) {
			throw ProblemaDeNegocio.requisicaoInvalida("senha-transacional-igual-a-de-acesso",
					"A senha de movimentacao deve ser diferente da senha de acesso.");
		}

		usuario.definirSenhaTransacional(this.senhas.encode(novaSenhaTransacional));
	}

	/** Nao faz nada se o titular ainda nao definiu uma senha de movimentacao. */
	@Transactional(readOnly = true)
	public void exigir(Long usuarioId, String senhaInformada) {
		Usuario usuario = carregar(usuarioId);

		if (!usuario.exigeSenhaTransacional()) {
			return;
		}
		if (senhaInformada == null || !this.senhas.matches(senhaInformada, usuario.getSenhaTransacionalHash())) {
			throw ProblemaDeNegocio.naoAutorizado("senha-transacional-invalida",
					"Informe a senha de movimentacao no cabecalho X-Senha-Transacional.");
		}
	}

	private Usuario carregar(Long usuarioId) {
		return this.usuarios.findById(usuarioId)
			.orElseThrow(() -> ProblemaDeNegocio.naoEncontrado("usuario-nao-encontrado", "Usuario nao encontrado."));
	}

}
