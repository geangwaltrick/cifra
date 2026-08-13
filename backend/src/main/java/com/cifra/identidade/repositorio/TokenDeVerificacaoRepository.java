package com.cifra.identidade.repositorio;

import java.util.Optional;

import com.cifra.identidade.dominio.TokenDeVerificacao;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenDeVerificacaoRepository extends JpaRepository<TokenDeVerificacao, Long> {

	Optional<TokenDeVerificacao> findByTokenHash(String tokenHash);

}
