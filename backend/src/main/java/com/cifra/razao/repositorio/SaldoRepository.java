package com.cifra.razao.repositorio;

import java.util.List;

import com.cifra.razao.dominio.Saldo;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaldoRepository extends JpaRepository<Saldo, Long> {

	/**
	 * Trava as linhas de saldo com SELECT ... FOR UPDATE, sempre na mesma ordem.
	 *
	 * <p>O {@code order by} nao e cosmetico. Sem ele, uma transferencia de A para
	 * B simultanea a uma de B para A trava cada uma a sua primeira conta e as
	 * duas ficam esperando a outra -- deadlock. Travando sempre em ordem
	 * crescente de conta, a segunda transacao espera na primeira linha e o ciclo
	 * nunca se forma. No plano do Postgres o LockRows fica acima do Sort, entao
	 * as linhas sao travadas ja ordenadas.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from Saldo s where s.contaId in :contas order by s.contaId")
	List<Saldo> travarPorContas(@Param("contas") List<Long> contas);

}
