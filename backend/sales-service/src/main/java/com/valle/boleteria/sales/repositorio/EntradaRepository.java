package com.valle.boleteria.sales.repositorio;

import com.valle.boleteria.sales.entidad.Entrada;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntradaRepository extends JpaRepository<Entrada, Long> {
}
