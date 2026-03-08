package de.caluga.morphium.quarkus.it;

import jakarta.data.repository.BasicRepository;
import jakarta.data.repository.Param;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;

import java.util.List;

/**
 * Jakarta Data repository for {@link OrderEntity}.
 * Tests query derivation with various operators and JDQL queries.
 */
@Repository
public interface OrderRepository extends BasicRepository<OrderEntity, String> {

    // -- Phase 2: Query derivation methods --

    List<OrderEntity> findByStatus(String status);

    List<OrderEntity> findByAmountGreaterThan(double minAmount);

    List<OrderEntity> findByAmountGreaterThanEqual(double minAmount);

    List<OrderEntity> findByAmountLessThan(double maxAmount);

    List<OrderEntity> findByStatusAndAmountGreaterThan(String status, double minAmount);

    long countByStatus(String status);

    boolean existsByStatus(String status);

    // -- Phase 5: @Query with JDQL --

    @Query("WHERE status = :status ORDER BY amount ASC")
    List<OrderEntity> queryByStatus(@Param("status") String status);

    @Query("WHERE status = :status AND amount > :minAmount")
    List<OrderEntity> queryByStatusAndMinAmount(@Param("status") String status,
                                                 @Param("minAmount") double minAmount);

    @Query("WHERE amount BETWEEN :min AND :max ORDER BY amount DESC")
    List<OrderEntity> queryByAmountRange(@Param("min") double min, @Param("max") double max);

    @Query("WHERE amount >= :minAmount")
    long countByMinAmount(@Param("minAmount") double minAmount);

    @Query("WHERE status = :status")
    boolean existsWithStatus(@Param("status") String status);

    @Query("WHERE customerId IS NOT NULL ORDER BY customerId ASC")
    List<OrderEntity> queryAllWithCustomerId();

    @Query("WHERE status = :s1 OR status = :s2")
    List<OrderEntity> queryByEitherStatus(@Param("s1") String status1,
                                           @Param("s2") String status2);
}
