package ru.mirea.library.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mirea.library.domain.Reader;

import java.util.UUID;

public interface ReaderRepository extends JpaRepository<Reader, UUID> {
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);
    boolean existsByLibraryCardNumber(String libraryCardNumber);
    boolean existsByLibraryCardNumberAndIdNot(String libraryCardNumber, UUID id);
}
