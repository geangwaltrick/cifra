package com.cifra.pix;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cifra.DadosDeTeste;
import com.cifra.TestcontainersConfiguration;
import com.cifra.comum.auditoria.AuditoriaRepository;
import com.cifra.identidade.aplicacao.ServicoDeEmail;
import com.cifra.identidade.dominio.Usuario;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/** Chaves PIX, extrato, limites e senha de movimentacao, pela API. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Produto bancario")
class ProdutoBancarioIT {

	private static final ParameterizedTypeReference<Map<String, Object>> JSON =
			new ParameterizedTypeReference<>() {
			};

	private static final String SENHA = "senha-bem-comprida-1";

	@Autowired
	private Environment ambiente;

	@Autowired
	private AuditoriaRepository auditoria;

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

	// --- chaves -------------------------------------------------------------

	@Test
	@DisplayName("chave aleatoria e gerada pelo sistema, nao pelo cliente")
	void chave_aleatoria() {
		Cliente cliente = clientePronto();

		ResponseEntity<Map<String, Object>> resposta = registrarChave(cliente, "ALEATORIA", "tento-escolher-eu");

		assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(resposta.getBody()).containsEntry("tipo", "ALEATORIA");
		assertThat(resposta.getBody().get("valor")).asString()
			.isNotEqualTo("tento-escolher-eu")
			.matches("[0-9a-f-]{36}");
	}

	@Test
	@DisplayName("chave de CPF precisa ser o CPF do proprio titular")
	void chave_de_cpf_de_outro() {
		Cliente cliente = clientePronto();

		ResponseEntity<Map<String, Object>> resposta = registrarChave(cliente, "CPF", DadosDeTeste.cpfValido());

		assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(resposta.getBody()).containsEntry("title", "chave-pix-nao-pertence-ao-titular");
	}

	@Test
	@DisplayName("o proprio CPF e aceito, com ou sem pontuacao")
	void chave_de_cpf_proprio() {
		Cliente cliente = clientePronto();

		ResponseEntity<Map<String, Object>> resposta = registrarChave(cliente, "CPF", cliente.cpf());

		assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(resposta.getBody()).containsEntry("valor", cliente.cpf());
	}

	@Test
	@DisplayName("uma chave endereca uma conta so")
	void chave_duplicada() {
		Cliente primeiro = clientePronto();
		Cliente segundo = clientePronto();

		registrarChave(primeiro, "TELEFONE", "11988887777");
		ResponseEntity<Map<String, Object>> repetida = registrarChave(segundo, "TELEFONE", "11988887777");

		assertThat(repetida.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(repetida.getBody()).containsEntry("title", "chave-pix-em-uso");
	}

	// --- pagamento ----------------------------------------------------------

	@Test
	@DisplayName("PIX por chave move o dinheiro e fica auditado")
	void pix_por_chave() {
		Cliente pagador = clientePronto();
		Cliente recebedor = clientePronto();
		depositar(pagador, "500.00");

		String chave = (String) registrarChave(recebedor, "EMAIL", recebedor.email()).getBody().get("valor");

		ResponseEntity<Map<String, Object>> resposta = pagarPix(pagador, chave, "175.50", null);

		assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(resposta.getBody()).containsEntry("tipo", "PIX");
		assertThat(resposta.getBody()).extracting("somaDosLancamentos").asString().isEqualTo("0.00");
		assertThat(saldo(pagador)).isEqualTo("324.50");
		assertThat(saldo(recebedor)).isEqualTo("175.50");

		assertThat(this.auditoria.findByAcaoOrderByCriadoEmDesc("PIX_ENVIADO"))
			.as("o envio precisa deixar rastro")
			.isNotEmpty();
	}

	@Test
	@DisplayName("chave inexistente devolve problema tipado")
	void pix_para_chave_inexistente() {
		Cliente pagador = clientePronto();
		depositar(pagador, "100.00");

		ResponseEntity<Map<String, Object>> resposta = pagarPix(pagador, "ninguem@lugar.nenhum", "10.00", null);

		assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(resposta.getBody()).containsEntry("title", "chave-pix-nao-encontrada");
	}

	// --- extrato ------------------------------------------------------------

	@Test
	@DisplayName("extrato traz saldo corrente linha a linha e a contraparte")
	void extrato_com_saldo_corrente() {
		Cliente cliente = clientePronto();
		depositar(cliente, "1000.00");
		sacar(cliente, "250.00", null);
		depositar(cliente, "125.50");

		Map<String, Object> extrato = extrato(cliente, "").getBody();

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> linhas = (List<Map<String, Object>>) extrato.get("linhas");

		assertThat(extrato).containsEntry("total", 3);
		assertThat(linhas).hasSize(3);

		// Mais recente primeiro: 1000 - 250 + 125,50 = 875,50
		assertThat(linhas.get(0)).containsEntry("saldoApos", "875.50").containsEntry("sentido", "CREDITO");
		assertThat(linhas.get(1)).containsEntry("saldoApos", "750.00").containsEntry("sentido", "DEBITO");
		assertThat(linhas.get(2)).containsEntry("saldoApos", "1000.00");
		assertThat(linhas.get(0).get("contraparte")).asString().isNotEmpty();
	}

	@Test
	@DisplayName("extrato filtra por tipo sem perder o saldo corrente do historico")
	void extrato_filtrado() {
		Cliente cliente = clientePronto();
		depositar(cliente, "1000.00");
		sacar(cliente, "250.00", null);

		Map<String, Object> extrato = extrato(cliente, "&tipo=SAQUE").getBody();

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> linhas = (List<Map<String, Object>>) extrato.get("linhas");

		assertThat(extrato).containsEntry("total", 1);
		// 750, nao -250: a janela roda sobre o historico inteiro e so depois filtra.
		assertThat(linhas.get(0)).containsEntry("saldoApos", "750.00").containsEntry("tipo", "SAQUE");
	}

	// --- limite -------------------------------------------------------------

	@Test
	@DisplayName("limite diario informa quanto ainda cabe hoje")
	void limite_disponivel() {
		Cliente cliente = clientePronto();
		depositar(cliente, "10000.00");
		sacar(cliente, "1200.00", null);

		Map<String, Object> limite = consultarLimite(cliente).getBody();

		assertThat(limite).containsEntry("limiteDiario", "5000.00")
			.containsEntry("gastoHoje", "1200.00")
			.containsEntry("disponivelHoje", "3800.00");
	}

	@Test
	@DisplayName("saida acima do limite e recusada com o disponivel na mensagem")
	void limite_excedido() {
		Cliente cliente = clientePronto();
		depositar(cliente, "20000.00");

		ResponseEntity<Map<String, Object>> resposta = sacar(cliente, "5000.01", null);

		assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(resposta.getBody()).containsEntry("title", "limite-diario-excedido");
		assertThat(resposta.getBody().get("detail")).asString().contains("5000.00");
	}

	// --- senha de movimentacao ----------------------------------------------

	@Test
	@DisplayName("depois de definida, a senha de movimentacao passa a ser exigida")
	void senha_transacional() {
		Cliente cliente = clientePronto();
		depositar(cliente, "1000.00");

		// Antes de definir, sai sem senha nenhuma.
		assertThat(sacar(cliente, "10.00", null).getStatusCode()).isEqualTo(HttpStatus.CREATED);

		definirSenhaTransacional(cliente, "movimento-123");

		ResponseEntity<Map<String, Object>> semSenha = sacar(cliente, "10.00", null);
		assertThat(semSenha.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(semSenha.getBody()).containsEntry("title", "senha-transacional-invalida");

		assertThat(sacar(cliente, "10.00", "errada").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(sacar(cliente, "10.00", "movimento-123").getStatusCode()).isEqualTo(HttpStatus.CREATED);
	}

	@Test
	@DisplayName("consultar saldo continua livre: a segunda senha e so para mover dinheiro")
	void senha_transacional_nao_bloqueia_leitura() {
		Cliente cliente = clientePronto();
		definirSenhaTransacional(cliente, "movimento-123");

		assertThat(consultarSaldo(cliente).getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(extrato(cliente, "").getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	// --- auxiliares ---------------------------------------------------------

	private record Cliente(String token, String email, String cpf) {
	}

	private Cliente clientePronto() {
		String email = DadosDeTeste.emailUnico();
		String cpf = DadosDeTeste.cpfValido();

		ResponseEntity<Map<String, Object>> registro = this.http.post()
			.uri("/api/v1/auth/registro")
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("nome", "Titular de Teste", "cpf", cpf, "email", email, "senha", SENHA))
			.retrieve()
			.toEntity(JSON);

		// Falhar aqui e nao no verify do Mockito: um cadastro recusado vira
		// "wanted but not invoked", que nao diz nada sobre o que deu errado.
		assertThat(registro.getStatusCode()).as("cadastro: %s", registro.getBody())
			.isEqualTo(HttpStatus.CREATED);

		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		verify(this.email, Mockito.atLeastOnce()).enviarVerificacao(any(Usuario.class), captor.capture());

		this.http.get()
			.uri((uri) -> uri.path("/api/v1/auth/verificar-email").queryParam("token", captor.getValue()).build())
			.retrieve()
			.toBodilessEntity();

		String token = (String) this.http.post()
			.uri("/api/v1/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("email", email, "senha", SENHA))
			.retrieve()
			.toEntity(JSON)
			.getBody()
			.get("accessToken");

		return new Cliente(token, email, cpf);
	}

	private ResponseEntity<Map<String, Object>> registrarChave(Cliente cliente, String tipo, String valor) {
		return this.http.post()
			.uri("/api/v1/pix/chaves")
			.header("Authorization", "Bearer " + cliente.token())
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("tipo", tipo, "valor", valor))
			.retrieve()
			.toEntity(JSON);
	}

	private ResponseEntity<Map<String, Object>> pagarPix(Cliente cliente, String chave, String valor, String senha) {
		return this.http.post()
			.uri("/api/v1/pix/transferencias")
			.header("Authorization", "Bearer " + cliente.token())
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.headers((cabecalhos) -> {
				if (senha != null) {
					cabecalhos.add("X-Senha-Transacional", senha);
				}
			})
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("chave", chave, "valor", valor, "descricao", "pix de teste"))
			.retrieve()
			.toEntity(JSON);
	}

	private ResponseEntity<Map<String, Object>> depositar(Cliente cliente, String valor) {
		return this.http.post()
			.uri("/api/v1/depositos")
			.header("Authorization", "Bearer " + cliente.token())
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("valor", valor, "descricao", "carga de teste"))
			.retrieve()
			.toEntity(JSON);
	}

	private ResponseEntity<Map<String, Object>> sacar(Cliente cliente, String valor, String senha) {
		return this.http.post()
			.uri("/api/v1/saques")
			.header("Authorization", "Bearer " + cliente.token())
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.headers((cabecalhos) -> {
				if (senha != null) {
					cabecalhos.add("X-Senha-Transacional", senha);
				}
			})
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("valor", valor, "descricao", "saque de teste"))
			.retrieve()
			.toEntity(JSON);
	}

	private void definirSenhaTransacional(Cliente cliente, String senha) {
		ResponseEntity<Map<String, Object>> resposta = this.http.post()
			.uri("/api/v1/seguranca/senha-transacional")
			.header("Authorization", "Bearer " + cliente.token())
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("senhaDeAcesso", SENHA, "senhaTransacional", senha))
			.retrieve()
			.toEntity(JSON);

		assertThat(resposta.getStatusCode()).as("corpo: %s", resposta.getBody()).isEqualTo(HttpStatus.OK);
	}

	private ResponseEntity<Map<String, Object>> extrato(Cliente cliente, String filtros) {
		return this.http.get()
			.uri("/api/v1/contas/me/extrato?tamanho=50" + filtros)
			.header("Authorization", "Bearer " + cliente.token())
			.retrieve()
			.toEntity(JSON);
	}

	private ResponseEntity<Map<String, Object>> consultarLimite(Cliente cliente) {
		return this.http.get()
			.uri("/api/v1/contas/me/limite")
			.header("Authorization", "Bearer " + cliente.token())
			.retrieve()
			.toEntity(JSON);
	}

	private ResponseEntity<Map<String, Object>> consultarSaldo(Cliente cliente) {
		return this.http.get()
			.uri("/api/v1/contas/me/saldo")
			.header("Authorization", "Bearer " + cliente.token())
			.retrieve()
			.toEntity(JSON);
	}

	private String saldo(Cliente cliente) {
		return (String) consultarSaldo(cliente).getBody().get("saldo");
	}

}
