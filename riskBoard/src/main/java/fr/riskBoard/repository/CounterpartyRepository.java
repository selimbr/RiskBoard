package fr.riskBoard.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import fr.riskBoard.entities.Counterparty;

public interface CounterpartyRepository extends JpaRepository<Counterparty, Long> {

    Optional<Counterparty> findByRicosCode(String ricosCode);
}
