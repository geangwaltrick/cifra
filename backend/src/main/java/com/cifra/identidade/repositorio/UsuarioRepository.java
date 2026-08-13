package com.cifra.identidade.repositorio;

import java.util.Optional;

import com.cifra.identidade.dominio.Cpf;
import com.cifra.identidade.dominio.Usuario;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	Optional<Usuario> findByEmail(String email);

	boolean existsByEmail(String email);

	boolean existsByCpf(Cpf cpf);

}
