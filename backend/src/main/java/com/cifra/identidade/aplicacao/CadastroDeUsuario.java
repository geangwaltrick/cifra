package com.cifra.identidade.aplicacao;

import java.time.Instant;

import com.cifra.comum.ProblemaDeNegocio;
import com.cifra.comum.TokensOpacos;
import com.cifra.configuracao.PropriedadesDoCifra;
import com.cifra.identidade.dominio.Conta;
import com.cifra.identidade.dominio.Cpf;
import com.cifra.identidade.dominio.TipoConta;
import com.cifra.identidade.dominio.TokenDeVerificacao;
import com.cifra.identidade.dominio.Usuario;
import com.cifra.identidade.repositorio.ContaRepository;
import com.cifra.identidade.repositorio.TokenDeVerificacaoRepository;
import com.cifra.identidade.repositorio.UsuarioRepository;
import com.cifra.razao.dominio.Limite;
import com.cifra.razao.dominio.Saldo;
import com.cifra.razao.repositorio.LimiteRepository;
import com.cifra.razao.repositorio.SaldoRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cadastro do usuario e abertura da conta, no mesmo ato. */
@Service
public class CadastroDeUsuario {

	private final UsuarioRepository usuarios;

	private final ContaRepository contas;

	private final TokenDeVerificacaoRepository tokens;

	private final SaldoRepository saldos;

	private final LimiteRepository limites;

	private final GeradorDeNumeroDeConta gerador;

	private final PasswordEncoder senhas;

	private final ServicoDeEmail email;

	private final PropriedadesDoCifra propriedades;

	public CadastroDeUsuario(UsuarioRepository usuarios, ContaRepository contas,
			TokenDeVerificacaoRepository tokens, SaldoRepository saldos, LimiteRepository limites,
			GeradorDeNumeroDeConta gerador, PasswordEncoder senhas, ServicoDeEmail email,
			PropriedadesDoCifra propriedades) {
		this.usuarios = usuarios;
		this.contas = contas;
		this.tokens = tokens;
		this.saldos = saldos;
		this.limites = limites;
		this.gerador = gerador;
		this.senhas = senhas;
		this.email = email;
		this.propriedades = propriedades;
	}

	@Transactional
	public Resultado cadastrar(String nome, String cpfInformado, String emailInformado, String senha) {
		Cpf cpf = Cpf.de(cpfInformado);
		String email = Usuario.normalizarEmail(emailInformado);

		// Checagem antecipada da a mensagem boa; a garantia real e a unique
		// constraint logo abaixo, que e quem resolve duas requisicoes simultaneas.
		if (this.usuarios.existsByEmail(email)) {
			throw ProblemaDeNegocio.conflito("email-ja-cadastrado", "Ja existe uma conta com este e-mail.");
		}
		if (this.usuarios.existsByCpf(cpf)) {
			throw ProblemaDeNegocio.conflito("cpf-ja-cadastrado", "Ja existe uma conta com este CPF.");
		}

		Usuario usuario = new Usuario(nome.trim(), cpf, email, this.senhas.encode(senha));
		try {
			this.usuarios.saveAndFlush(usuario);
		}
		catch (DataIntegrityViolationException ex) {
			throw ProblemaDeNegocio.conflito("cadastro-duplicado",
					"Ja existe uma conta com este e-mail ou CPF.");
		}

		Conta conta = new Conta(usuario, GeradorDeNumeroDeConta.AGENCIA_PADRAO,
				this.gerador.proximoNumero(), TipoConta.CORRENTE);
		this.contas.save(conta);

		// Conta nasce com sua linha de saldo. Sem ela o razao nao tem onde
		// projetar o resultado e a primeira movimentacao falharia.
		this.saldos.save(new Saldo(conta.getId(), false));
		this.limites.save(Limite.padraoPara(conta.getId()));

		enviarVerificacao(usuario);

		return new Resultado(usuario, conta);
	}

	private void enviarVerificacao(Usuario usuario) {
		String token = TokensOpacos.gerar();
		Instant expiraEm = Instant.now().plus(this.propriedades.email().validadeDaVerificacao());

		this.tokens.save(new TokenDeVerificacao(usuario, TokensOpacos.hashDe(token), expiraEm));
		this.email.enviarVerificacao(usuario, token);
	}

	public record Resultado(Usuario usuario, Conta conta) {
	}

}
