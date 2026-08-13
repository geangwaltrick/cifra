package com.cifra.identidade.aplicacao;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.cifra.configuracao.PropriedadesDoCifra;
import com.cifra.identidade.dominio.Usuario;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Envio do e-mail de confirmacao.
 *
 * <p>Falha de e-mail nao derruba o cadastro: a conta ja esta criada e o link
 * pode ser reenviado. Estourar aqui desfaria um cadastro valido por causa de
 * um servidor SMTP fora do ar.
 */
@Component
public class ServicoDeEmail {

	private static final Log logger = LogFactory.getLog(ServicoDeEmail.class);

	private final ObjectProvider<JavaMailSender> remetentes;

	private final PropriedadesDoCifra propriedades;

	public ServicoDeEmail(ObjectProvider<JavaMailSender> remetentes, PropriedadesDoCifra propriedades) {
		this.remetentes = remetentes;
		this.propriedades = propriedades;
	}

	public void enviarVerificacao(Usuario usuario, String token) {
		String link = "%s/api/v1/auth/verificar-email?token=%s".formatted(
				this.propriedades.urlBase(),
				URLEncoder.encode(token, StandardCharsets.UTF_8));

		JavaMailSender remetente = this.remetentes.getIfAvailable();
		if (remetente == null) {
			logger.warn("Sem servidor de e-mail configurado. Link de verificacao: " + link);
			return;
		}

		try {
			SimpleMailMessage mensagem = new SimpleMailMessage();
			mensagem.setFrom(this.propriedades.email().remetente());
			mensagem.setTo(usuario.getEmail());
			mensagem.setSubject("Confirme seu e-mail no Cifra");
			mensagem.setText("""
					Ola, %s.

					Confirme seu e-mail para ativar sua conta no Cifra:

					%s

					O link vale por %d horas. Se nao foi voce quem se cadastrou, ignore esta mensagem.
					""".formatted(usuario.getNome(), link, this.propriedades.email().validadeDaVerificacao().toHours()));

			remetente.send(mensagem);
		}
		catch (RuntimeException ex) {
			logger.warn("Falha ao enviar verificacao para " + usuario.getEmail() + ": " + ex.getMessage());
		}
	}

}
