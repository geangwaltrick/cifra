package com.cifra.razao;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cifra.DadosDeTeste;
import com.cifra.TestcontainersConfiguration;
import com.cifra.identidade.aplicacao.ServicoDeEmail;
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

/** A superficie HTTP do razao: idempotencia por header e saldo conferido. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Movimentacao pela API")
class MovimentacaoIT {

	private static final ParameterizedTypeReference<Map<String, Object>> JSON =
			new ParameterizedTypeReference<>() {
			};

	private static final String SENHA = "senha-bem-comprida-1";

	@Autowired
	private Environment ambiente;

	@MockitoBean
	private ServicoDeEmail email;

	private RestClient http;

	@BeforeEach
	void prepararCliente() {
		this.http = RestClient.builder()
			.baseUrl("http://localhost:" + this.ambiente.getProperty("local.server.port"))
			.defaultStatusHandler((status) -> true, (requisicao, resposta) -> {
			})
			.build();
	}

	@Test
	@DisplayName("deposito devolve os dois lancamentos somando zero")
	void deposito_expoe_o_par_de_lancamentos() {
		String token = contaPronta();

		ResponseEntity<Map<String, Object>> resposta = depositar(token, "250.00", UUID.randomUUID().toString());

		assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(resposta.getBody()).containsEntry("tipo", "DEPOSITO").containsEntry("status", "LIQUIDADA");
		assertThat(resposta.getBody()).extracting("somaDosLancamentos").asString().isEqualTo("0.00");
		assertThat((List<?>) resposta.getBody().get("lancamentos")).hasSize(2);
	}

	@Test
	@DisplayName("mesmo Idempotency-Key devolve a mesma transacao e credita uma vez so")
	void idempotency_key_no_header() {
		String token = contaPronta();
		String chave = UUID.randomUUID().toString();

		ResponseEntity<Map<String, Object>> primeira = depositar(token, "80.00", chave);
		ResponseEntity<Map<String, Object>> repetida = depositar(token, "80.00", chave);

		assertThat(repetida.getBody().get("id")).isEqualTo(primeira.getBody().get("id"));
		assertThat(saldo(token).getBody()).extracting("saldo").asString().isEqualTo("80.00");
	}

	@Test
	@DisplayName("o saldo exibido e o mesmo conferido no razao")
	void saldo_confere_com_o_razao() {
		String token = contaPronta();
		depositar(token, "500.00", UUID.randomUUID().toString());
		sacar(token, "125.25", UUID.randomUUID().toString());

		Map<String, Object> corpo = saldo(token).getBody();

		assertThat(corpo).extracting("saldo").asString().isEqualTo("374.75");
		assertThat(corpo.get("saldoConferidoNoRazao")).isEqualTo(corpo.get("saldo"));
	}

	@Test
	@DisplayName("saque sem saldo devolve problema tipado")
	void saque_sem_saldo() {
		String token = contaPronta();
		depositar(token, "10.00", UUID.randomUUID().toString());

		ResponseEntity<Map<String, Object>> resposta = sacar(token, "10.01", UUID.randomUUID().toString());

		assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(resposta.getBody()).containsEntry("title", "saldo-insuficiente");
	}

	@Test
	@DisplayName("transferencia por agencia e numero move o dinheiro entre as contas")
	void transferencia_entre_contas() {
		String origem = contaPronta();
		String destino = contaPronta();
		depositar(origem, "300.00", UUID.randomUUID().toString());

		Map<String, Object> contaDestino = minhaConta(destino).getBody();

		ResponseEntity<Map<String, Object>> resposta = this.http.post()
			.uri("/api/v1/transferencias")
			.header("Authorization", "Bearer " + origem)
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("agenciaDestino", contaDestino.get("agencia"), "contaDestino", contaDestino.get("numero"),
					"valor", "120.00", "descricao", "pagamento"))
			.retrieve()
			.toEntity(JSON);

		assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(resposta.getBody()).extracting("somaDosLancamentos").asString().isEqualTo("0.00");
		assertThat(saldo(origem).getBody()).extracting("saldo").asString().isEqualTo("180.00");
		assertThat(saldo(destino).getBody()).extracting("saldo").asString().isEqualTo("120.00");
	}

	@Test
	@DisplayName("a saude da aplicacao reporta se os livros fecham")
	void saude_inclui_o_razao() {
		String token = contaPronta();
		depositar(token, "42.00", UUID.randomUUID().toString());

		ResponseEntity<Map<String, Object>> saude = this.http.get()
			.uri("/actuator/health")
			.retrieve()
			.toEntity(JSON);

		assertThat(saude.getBody()).containsEntry("status", "UP");
		assertThat(saude.getBody()).extractingByKey("components", as(MAP))
			.extractingByKey("razao", as(MAP))
			.containsEntry("status", "UP");
	}

	// --- auxiliares ---------------------------------------------------------

	/** Cadastra, confirma o e-mail, autentica e devolve o access token. */
	private String contaPronta() {
		String email = DadosDeTeste.emailUnico();

		this.http.post()
			.uri("/api/v1/auth/registro")
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("nome", "Titular de Teste", "cpf", DadosDeTeste.cpfValido(), "email", email, "senha", SENHA))
			.retrieve()
			.toBodilessEntity();

		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		verify(this.email, org.mockito.Mockito.atLeastOnce()).enviarVerificacao(any(Usuario.class), captor.capture());

		this.http.get()
			.uri((uri) -> uri.path("/api/v1/auth/verificar-email").queryParam("token", captor.getValue()).build())
			.retrieve()
			.toBodilessEntity();

		return (String) this.http.post()
			.uri("/api/v1/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("email", email, "senha", SENHA))
			.retrieve()
			.toEntity(JSON)
			.getBody()
			.get("accessToken");
	}

	private ResponseEntity<Map<String, Object>> depositar(String token, String valor, String chave) {
		return movimentar("/api/v1/depositos", token, valor, chave);
	}

	private ResponseEntity<Map<String, Object>> sacar(String token, String valor, String chave) {
		return movimentar("/api/v1/saques", token, valor, chave);
	}

	private ResponseEntity<Map<String, Object>> movimentar(String rota, String token, String valor, String chave) {
		return this.http.post()
			.uri(rota)
			.header("Authorization", "Bearer " + token)
			.header("Idempotency-Key", chave)
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("valor", valor, "descricao", "movimento de teste"))
			.retrieve()
			.toEntity(JSON);
	}

	private ResponseEntity<Map<String, Object>> saldo(String token) {
		return this.http.get()
			.uri("/api/v1/contas/me/saldo")
			.header("Authorization", "Bearer " + token)
			.retrieve()
			.toEntity(JSON);
	}

	private ResponseEntity<Map<String, Object>> minhaConta(String token) {
		return this.http.get()
			.uri("/api/v1/contas/me")
			.header("Authorization", "Bearer " + token)
			.retrieve()
			.toEntity(JSON);
	}

}
