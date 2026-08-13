package com.cifra.identidade;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import com.cifra.TestcontainersConfiguration;
import com.cifra.identidade.aplicacao.ServicoDeEmail;
import com.cifra.identidade.dominio.Cpf;
import com.cifra.identidade.dominio.Usuario;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * Fluxo de identidade ponta a ponta contra um PostgreSQL real.
 *
 * <p>Cada teste usa e-mail e CPF proprios, entao a ordem de execucao nao importa
 * e nao ha limpeza entre cenarios.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Identidade")
class IdentidadeIT {

	private static final ParameterizedTypeReference<Map<String, Object>> JSON =
			new ParameterizedTypeReference<>() {
			};

	private static final AtomicInteger SEQUENCIA = new AtomicInteger();

	private static final String SENHA = "senha-bem-comprida-1";

	@Autowired
	private Environment ambiente;

	/** Interceptado para capturar o token de verificacao, que so sai por e-mail. */
	@MockitoBean
	private ServicoDeEmail email;

	private RestClient http;

	@BeforeEach
	void prepararCliente() {
		this.http = RestClient.builder()
			.baseUrl("http://localhost:" + this.ambiente.getProperty("local.server.port"))
			// Sem isto o RestClient lanca em 4xx e o teste nao consegue inspecionar
			// o corpo do ProblemDetail, que e justamente o que se quer verificar.
			.defaultStatusHandler((status) -> true, (requisicao, resposta) -> {
			})
			.build();
	}

	@Test
	@DisplayName("cadastro cria usuario pendente e ja abre a conta corrente")
	void cadastro_abre_conta() {
		String email = emailUnico();

		ResponseEntity<Map<String, Object>> resposta = cadastrar(email, cpfValido());

		assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(resposta.getBody()).containsEntry("status", "PENDENTE_VERIFICACAO");
		assertThat(resposta.getBody()).containsEntry("email", email);
		assertThat(resposta.getBody()).extractingByKey("conta", as(MAP))
			.containsEntry("agencia", "0001")
			.containsEntry("status", "ATIVA")
			.containsEntry("tipo", "CORRENTE");
	}

	@Test
	@DisplayName("resposta do cadastro devolve o CPF mascarado")
	void cadastro_mascara_cpf() {
		String cpf = cpfValido();

		ResponseEntity<Map<String, Object>> resposta = cadastrar(emailUnico(), cpf);

		assertThat(resposta.getBody()).extracting("cpf").asString().doesNotContain(cpf).startsWith("***.");
	}

	@Test
	@DisplayName("login antes da verificacao do e-mail e recusado")
	void login_exige_email_verificado() {
		String email = emailUnico();
		cadastrar(email, cpfValido());

		ResponseEntity<Map<String, Object>> resposta = entrar(email, SENHA);

		assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(resposta.getBody()).containsEntry("title", "email-nao-verificado");
	}

	@Test
	@DisplayName("cadastro, verificacao, login e consulta da propria conta")
	void fluxo_completo() {
		String email = emailUnico();
		cadastrar(email, cpfValido());

		verificarEmail(tokenDeVerificacaoCapturado());

		ResponseEntity<Map<String, Object>> login = entrar(email, SENHA);
		assertThat(login.getStatusCode()).as("corpo do login: %s", login.getBody()).isEqualTo(HttpStatus.OK);
		assertThat(login.getBody()).containsEntry("tipo", "Bearer");
		assertThat(login.getBody()).containsKey("accessToken").containsKey("refreshToken");

		ResponseEntity<Map<String, Object>> conta = minhaConta((String) login.getBody().get("accessToken"));

		assertThat(conta.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(conta.getBody()).containsEntry("agencia", "0001");
		assertThat(conta.getBody()).extracting("identificacao").asString().startsWith("0001 / ");
	}

	@Test
	@DisplayName("consulta de conta sem token e recusada")
	void conta_exige_token() {
		ResponseEntity<Map<String, Object>> resposta = this.http.get()
			.uri("/api/v1/contas/me")
			.retrieve()
			.toEntity(JSON);

		assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	@DisplayName("refresh rotaciona o token e o reuso do antigo derruba a sessao")
	void refresh_rotaciona_e_detecta_reuso() {
		String email = emailUnico();
		cadastrar(email, cpfValido());
		verificarEmail(tokenDeVerificacaoCapturado());

		ResponseEntity<Map<String, Object>> login = entrar(email, SENHA);
		assertThat(login.getStatusCode()).as("corpo do login: %s", login.getBody()).isEqualTo(HttpStatus.OK);

		String primeiroRefresh = (String) login.getBody().get("refreshToken");

		ResponseEntity<Map<String, Object>> renovacao = renovar(primeiroRefresh);
		assertThat(renovacao.getStatusCode()).isEqualTo(HttpStatus.OK);

		String segundoRefresh = (String) renovacao.getBody().get("refreshToken");
		assertThat(segundoRefresh).isNotEqualTo(primeiroRefresh);

		// O token antigo reaparecendo so pode significar copia: a familia cai.
		ResponseEntity<Map<String, Object>> reuso = renovar(primeiroRefresh);
		assertThat(reuso.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(reuso.getBody()).containsEntry("title", "refresh-reutilizado");

		// E a revogacao sobreviveu a excecao: o token legitimo tambem morreu.
		ResponseEntity<Map<String, Object>> depois = renovar(segundoRefresh);
		assertThat(depois.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	@DisplayName("CPF invalido devolve problema tipado, nao erro generico")
	void cpf_invalido() {
		ResponseEntity<Map<String, Object>> resposta = cadastrar(emailUnico(), "11111111111");

		assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(resposta.getBody()).containsEntry("title", "cpf-invalido");
		assertThat(resposta.getBody()).extracting("type").asString().endsWith("/problemas/cpf-invalido");
	}

	@Test
	@DisplayName("e-mail ja cadastrado devolve conflito")
	void email_duplicado() {
		String email = emailUnico();
		cadastrar(email, cpfValido());

		ResponseEntity<Map<String, Object>> segunda = cadastrar(email, cpfValido());

		assertThat(segunda.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(segunda.getBody()).containsEntry("title", "email-ja-cadastrado");
	}

	@Test
	@DisplayName("campos vazios devolvem os erros por campo")
	void campos_invalidos() {
		ResponseEntity<Map<String, Object>> resposta = this.http.post()
			.uri("/api/v1/auth/registro")
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("nome", "", "cpf", "", "email", "nao-e-email", "senha", "curta"))
			.retrieve()
			.toEntity(JSON);

		assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(resposta.getBody()).containsEntry("title", "campos-invalidos");
		assertThat(resposta.getBody()).extractingByKey("campos", as(MAP))
			.containsKeys("nome", "cpf", "email", "senha");
	}

	@Test
	@DisplayName("forca bruta no login e barrada apos o limite da janela")
	void limite_de_tentativas() {
		String email = emailUnico();
		cadastrar(email, cpfValido());
		verificarEmail(tokenDeVerificacaoCapturado());

		for (int tentativa = 1; tentativa <= 5; tentativa++) {
			assertThat(entrar(email, "senha-errada-de-proposito").getStatusCode())
				.as("tentativa %d ainda dentro da janela", tentativa)
				.isEqualTo(HttpStatus.UNAUTHORIZED);
		}

		ResponseEntity<Map<String, Object>> excedida = entrar(email, "senha-errada-de-proposito");

		assertThat(excedida.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(excedida.getBody()).containsEntry("title", "excesso-de-tentativas");
	}

	@Test
	@DisplayName("senha errada e e-mail inexistente devolvem a mesma resposta")
	void nao_revela_se_o_email_existe() {
		String email = emailUnico();
		cadastrar(email, cpfValido());
		verificarEmail(tokenDeVerificacaoCapturado());

		Map<String, Object> senhaErrada = entrar(email, "senha-errada-de-proposito").getBody();
		Map<String, Object> emailInexistente = entrar(emailUnico(), SENHA).getBody();

		assertThat(senhaErrada).containsEntry("title", "credenciais-invalidas");
		assertThat(emailInexistente.get("title")).isEqualTo(senhaErrada.get("title"));
		assertThat(emailInexistente.get("detail")).isEqualTo(senhaErrada.get("detail"));
	}

	// --- auxiliares ---------------------------------------------------------

	private ResponseEntity<Map<String, Object>> cadastrar(String email, String cpf) {
		return this.http.post()
			.uri("/api/v1/auth/registro")
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("nome", "Pessoa de Teste", "cpf", cpf, "email", email, "senha", SENHA))
			.retrieve()
			.toEntity(JSON);
	}

	private ResponseEntity<Map<String, Object>> entrar(String email, String senha) {
		return this.http.post()
			.uri("/api/v1/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("email", email, "senha", senha))
			.retrieve()
			.toEntity(JSON);
	}

	private ResponseEntity<Map<String, Object>> renovar(String refreshToken) {
		return this.http.post()
			.uri("/api/v1/auth/refresh")
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("refreshToken", refreshToken))
			.retrieve()
			.toEntity(JSON);
	}

	private ResponseEntity<Map<String, Object>> minhaConta(String accessToken) {
		return this.http.get()
			.uri("/api/v1/contas/me")
			.header("Authorization", "Bearer " + accessToken)
			.retrieve()
			.toEntity(JSON);
	}

	private void verificarEmail(String token) {
		ResponseEntity<Map<String, Object>> resposta = this.http.get()
			.uri((uri) -> uri.path("/api/v1/auth/verificar-email").queryParam("token", token).build())
			.retrieve()
			.toEntity(JSON);

		assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	private String tokenDeVerificacaoCapturado() {
		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		verify(this.email).enviarVerificacao(any(Usuario.class), captor.capture());
		return captor.getValue();
	}

	private static String emailUnico() {
		return "teste+%d-%d@cifra.local".formatted(SEQUENCIA.incrementAndGet(), System.nanoTime() % 100_000);
	}

	/**
	 * Sorteia ate cair num CPF valido. Usa {@link Cpf#ehValido} de proposito, em
	 * vez de recalcular os verificadores aqui: a matematica e coberta por
	 * CpfTest com valores fixos, e duplicar o algoritmo no teste faria os dois
	 * errarem juntos. Acerta em ~121 tentativas.
	 */
	private static String cpfValido() {
		for (int tentativa = 0; tentativa < 10_000; tentativa++) {
			StringBuilder candidato = new StringBuilder(11);
			for (int i = 0; i < 11; i++) {
				candidato.append(ThreadLocalRandom.current().nextInt(10));
			}
			if (Cpf.ehValido(candidato.toString())) {
				return candidato.toString();
			}
		}
		throw new IllegalStateException("Nao foi possivel sortear um CPF valido.");
	}

}
