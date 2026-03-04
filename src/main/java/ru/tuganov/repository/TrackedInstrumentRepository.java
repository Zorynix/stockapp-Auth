package ru.tuganov.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.tuganov.entity.AppUser;
import ru.tuganov.entity.TrackedInstrument;

import java.util.List;
import java.util.UUID;

@Repository
public interface TrackedInstrumentRepository extends JpaRepository<TrackedInstrument, UUID> {

    List<TrackedInstrument> findAllByUser(AppUser user);
}
