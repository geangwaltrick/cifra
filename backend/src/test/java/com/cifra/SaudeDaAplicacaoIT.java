package com.cifra;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;

/**
 * Prova de fundacao: a aplicacao sobe contra um Postgres real, o Flyway aplica
 * o esquema e o endpoint de saude reporta banco no ar.
 *
 * Exige Docker em execucao. Roda na fase verify (failsafe), nao na test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SaudeDaAplicacaoIT {

	@Autowired
	private Environment ambiente;

	@Autowired
	private JdbcTemplate jdbc;

	private RestClient http() {
		return RestClient.create("http://localhost:" + ambiente.getProperty("local.server.port"));
	}

	private static final ParameterizedTypeReference<Map<String, Object>> JSON =
			new ParameterizedTypeReference<>() {
			};

	@Test
	void saude_reporta_aplicacao_e_banco_no_ar() {
		Map<String, Object> corpo = http().get().uri("/actuator/health").retrieve().body(JSON);

		assertThat(corpo).containsEntry("status", "UP");
		assertThat(corpo).extractingByKey("components", as(MAP))
				.extractingByKey("db", as(MAP))
				.containsEntry("status", "UP");
	}

	@Test
	void flyway_aplicou_o_esquema_de_identidade() {
		List<String> tabelas = jdbc.queryForList("""
				select table_name
				  from information_schema.tables
				 where table_schema = 'public'
				 order by table_name
				""", String.class);

		assertThat(tabelas).contains("usuarios", "contas", "flyway_schema_history");
	}

	@Test
	void migration_v1_esta_registrada_como_bem_sucedida() {
		Map<String, Object> v1 = jdbc.queryForMap("""
				select version, description, success
				  from flyway_schema_history
				 where version = '1'
				""");

		assertThat(v1).containsEntry("description", "identidade");
		assertThat(v1).containsEntry("success", true);
	}

	@Test
	void hibernate_validou_o_esquema_contra_as_migrations() {
		// ddl-auto=validate: se o mapeamento divergir do banco, o contexto nem sobe.
		// Chegar ate aqui com o contexto no ar ja e a asercao.
		assertThat(ambiente.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
	}

}
