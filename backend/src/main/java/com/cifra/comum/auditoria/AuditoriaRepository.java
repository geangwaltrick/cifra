package com.cifra.comum.auditoria;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriaRepository extends JpaRepository<RegistroDeAuditoria, Long> {

	List<RegistroDeAuditoria> findByAtorIdOrderByCriadoEmDesc(Long atorId);

	List<RegistroDeAuditoria> findByAcaoOrderByCriadoEmDesc(String acao);

}
