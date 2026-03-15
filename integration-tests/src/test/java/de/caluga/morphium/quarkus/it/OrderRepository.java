package de.caluga.morphium.quarkus.it;

import jakarta.data.repository.BasicRepository;
import jakarta.data.repository.Find;
import jakarta.data.repository.By;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Param;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

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

    // -- Phase 7: New query derivation operators --

    List<OrderEntity> findByTagsContains(String tag);

    List<OrderEntity> findByTagsNotContains(String tag);

    List<OrderEntity> findByTagsIsEmpty();

    List<OrderEntity> findByTagsIsNotEmpty();

    List<OrderEntity> findByTagsSize(int size);

    List<OrderEntity> findByCustomerIdMatches(String regex);

    List<OrderEntity> findByStatusIgnoreCase(String status);

    // -- deleteAll() no-arg --

    void deleteAll();

    // -- deleteBy* Query Derivation --

    long deleteByStatus(String status);

    void deleteByAmountLessThan(double maxAmount);

    boolean deleteByCustomerId(String customerId);

    // -- Single-result methods for exception testing --

    OrderEntity findByCustomerId(String customerId);

    @Find
    Optional<OrderEntity> findOptionalByCustomerId(@By("customerId") String customerId);

    @Query("WHERE customerId = :cid")
    OrderEntity queryByCustomerId(@Param("cid") String customerId);

    @Query("WHERE customerId = :cid")
    Optional<OrderEntity> queryOptionalByCustomerId(@Param("cid") String customerId);

    // --- #4 Test Coverage Extension ---

    List<OrderEntity> findByAmountLessThanEqual(double maxAmount);

    List<OrderEntity> findByStatusNot(String status);

    List<OrderEntity> findByAmountBetween(double min, double max);

    List<OrderEntity> findByStatusIn(Collection<String> statuses);

    List<OrderEntity> findByStatusNotIn(Collection<String> statuses);

    List<OrderEntity> findByCustomerIdStartsWith(String prefix);

    List<OrderEntity> findByCustomerIdEndsWith(String suffix);

    List<OrderEntity> findByCustomerIdLike(String pattern);

    List<OrderEntity> findByCustomerIdIsNull();

    List<OrderEntity> findByCustomerIdIsNotNull();

    List<OrderEntity> findByUrgentIsTrue();

    List<OrderEntity> findByUrgentIsFalse();

    List<OrderEntity> findByStatusOrCustomerId(String status, String customerId);

    List<OrderEntity> findByStatusOrderByAmountAscCustomerIdDesc(String status);

    Stream<OrderEntity> findByAmountGreaterThanEqualOrderByAmountAsc(double minAmount);

    // --- #6 Stream Support ---

    @Find
    @OrderBy("amount")
    Stream<OrderEntity> findStreamByStatus(@By("status") String status);

    @Query("WHERE status = :status ORDER BY amount ASC")
    Stream<OrderEntity> queryStreamByStatus(@Param("status") String status);

    // --- #7 JDQL SELECT with Projection ---

    @Query("SELECT customerId, amount WHERE status = :status ORDER BY amount ASC")
    List<OrderEntity> queryProjectedByStatus(@Param("status") String status);

    @Query("SELECT customerId, amount FROM OrderEntity WHERE status = :status ORDER BY amount ASC")
    List<OrderEntity> queryProjectedWithFrom(@Param("status") String status);

    @Query("SELECT customerId WHERE amount > :minAmount")
    Stream<OrderEntity> queryProjectedStream(@Param("minAmount") double minAmount);

    @Query("SELECT customerId, amount WHERE customerId = :cid")
    Optional<OrderEntity> queryProjectedSingle(@Param("cid") String customerId);

    // --- #8 JDQL Aggregate Functions ---

    @Query("SELECT COUNT(this) WHERE status = :status")
    long countByStatusJdql(@Param("status") String status);

    @Query("SELECT SUM(amount) WHERE status = :status")
    double sumAmountByStatus(@Param("status") String status);

    @Query("SELECT AVG(amount) WHERE status = :status")
    double avgAmountByStatus(@Param("status") String status);

    @Query("SELECT MIN(amount) WHERE status = :status")
    double minAmountByStatus(@Param("status") String status);

    @Query("SELECT MAX(amount) WHERE status = :status")
    double maxAmountByStatus(@Param("status") String status);

    @Query("SELECT COUNT(this) WHERE amount > :minAmount")
    long countByAmountGreaterThan(@Param("minAmount") double minAmount);

    // --- #9 Async (CompletionStage) Support ---

    // Query derivation → async
    CompletionStage<List<OrderEntity>> findByStatusAsync(String status);

    CompletionStage<Optional<OrderEntity>> findByCustomerIdAsync(String customerId);

    // @Find → async
    @Find
    @OrderBy("amount")
    CompletionStage<List<OrderEntity>> findAsyncByStatus(@By("status") String status);

    // @Query JDQL → async
    @Query("WHERE status = :status ORDER BY amount ASC")
    CompletionStage<List<OrderEntity>> queryByStatusAsync(@Param("status") String status);

    @Query("SELECT COUNT(this) WHERE status = :status")
    CompletionStage<Long> countByStatusAsync(@Param("status") String status);
}
