package com.reforma.domain.auditoria.repository;

import com.reforma.domain.auditoria.entity.Auditoria;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriaRepository extends JpaRepository<Auditoria, String> {}
