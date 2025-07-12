package com.example.db_setup.model.repositories;

import com.example.db_setup.model.Player;
import com.example.db_setup.model.PlayerProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlayerProgressRepository extends JpaRepository<PlayerProgress, Long> {
    Optional<PlayerProgress> findByPlayer(Player player);
}
