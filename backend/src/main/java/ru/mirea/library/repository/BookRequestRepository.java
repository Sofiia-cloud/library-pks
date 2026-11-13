package ru.mirea.library.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.mirea.library.domain.BookRequest;
import ru.mirea.library.model.RequestStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookRequestRepository extends JpaRepository<BookRequest, UUID>, JpaSpecificationExecutor<BookRequest> {
    @Override
    @EntityGraph(attributePaths = "reader")
    Optional<BookRequest> findById(UUID id);

    @Override
    @EntityGraph(attributePaths = "reader")
    List<BookRequest> findAll(Specification<BookRequest> specification, Sort sort);

    boolean existsByReader_Id(UUID readerId);
    boolean existsByReader_IdAndStatusIn(UUID readerId, Collection<RequestStatus> statuses);

    @Query("""
            select b from BookRequest b join fetch b.reader
            order by (case b.status
                when ru.mirea.library.model.RequestStatus.CREATED then 0
                when ru.mirea.library.model.RequestStatus.APPROVED then 1
                when ru.mirea.library.model.RequestStatus.ISSUED then 2
                when ru.mirea.library.model.RequestStatus.RETURNED then 3
                when ru.mirea.library.model.RequestStatus.CANCELLED then 4
                when ru.mirea.library.model.RequestStatus.REJECTED then 5
                end) * :direction, b.requestNumber
            """)
    List<BookRequest> findAllOrderedByStatus(@Param("direction") int direction);
}
