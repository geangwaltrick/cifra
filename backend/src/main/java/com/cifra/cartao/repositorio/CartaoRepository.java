package com.cifra.cartao.repositorio;

import java.util.Optional;

import com.cifra.cartao.dominio.Cartao;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CartaoRepository extends JpaRepository<Cartao, Long> {

	Optional<Cartao> findByContaId(Long contaId);

	boolean existsByNumero(String numero);

}
