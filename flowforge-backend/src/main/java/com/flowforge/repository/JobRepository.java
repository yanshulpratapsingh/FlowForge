package com.flowforge.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.flowforge.entity.Job;

import org.springframework.data.jpa.repository.Modifying;

public interface JobRepository extends JpaRepository<Job, UUID> {

    @Query(value = "SELECT * FROM jobs " +
                   "WHERE (status = 'QUEUED' OR status = 'RETRYING') " +
                   "  AND (scheduled_at IS NULL OR scheduled_at <= :now) " +
                   "ORDER BY " +
                   "  CASE WHEN EXTRACT(EPOCH FROM (:now - COALESCE(scheduled_at, created_at))) > :starvationThresholdSeconds THEN 1 ELSE 0 END DESC, " +
                   "  priority DESC, " +
                   "  COALESCE(scheduled_at, created_at) ASC, " +
                   "  id ASC " +
                   "LIMIT :limit " +
                   "FOR UPDATE SKIP LOCKED", 
           nativeQuery = true)
    List<Job> findExecutableJobsWithLock(
            @Param("now") LocalDateTime now, 
            @Param("limit") int limit,
            @Param("starvationThresholdSeconds") long starvationThresholdSeconds);

    @Modifying
    @Query("UPDATE Job j SET j.leaseUntil = :newLeaseUntil, j.updatedAt = :now " +
           "WHERE j.id = :id AND j.status = 'RUNNING' AND j.workerId = :workerId AND j.leaseUntil > :now")
    int extendLease(@Param("id") UUID id, 
                    @Param("workerId") String workerId, 
                    @Param("newLeaseUntil") LocalDateTime newLeaseUntil, 
                    @Param("now") LocalDateTime now);

    @Query(value = "SELECT * FROM jobs " +
                   "WHERE status = 'RUNNING' AND lease_until < :now " +
                   "FOR UPDATE SKIP LOCKED", 
           nativeQuery = true)
    List<Job> findExpiredJobsWithLock(@Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE Job j SET j.status = 'COMPLETED', j.updatedAt = :now " +
           "WHERE j.id = :id AND j.status = 'RUNNING' AND j.workerId = :workerId")
    int markCompleted(@Param("id") UUID id, 
                      @Param("workerId") String workerId, 
                      @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE Job j SET j.status = 'RETRYING', j.retryCount = :retryCount, j.scheduledAt = :scheduledAt, " +
           "j.lastErrorMessage = :errorMessage, j.lastFailedAt = :failedAt, j.updatedAt = :now, " +
           "j.workerId = null, j.leaseUntil = null, j.startedAt = null " +
           "WHERE j.id = :id AND j.status = 'RUNNING' AND j.workerId = :workerId")
    int markRetrying(@Param("id") UUID id, 
                     @Param("workerId") String workerId, 
                     @Param("retryCount") int retryCount, 
                     @Param("scheduledAt") LocalDateTime scheduledAt, 
                     @Param("errorMessage") String errorMessage, 
                     @Param("failedAt") LocalDateTime failedAt, 
                     @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE Job j SET j.status = 'DEAD_LETTER', j.retryCount = :retryCount, " +
           "j.lastErrorMessage = :errorMessage, j.lastFailedAt = :failedAt, j.updatedAt = :now, " +
           "j.workerId = null, j.leaseUntil = null, j.startedAt = null " +
           "WHERE j.id = :id AND j.status = 'RUNNING' AND j.workerId = :workerId")
    int markDeadLetter(@Param("id") UUID id, 
                       @Param("workerId") String workerId, 
                       @Param("retryCount") int retryCount, 
                       @Param("errorMessage") String errorMessage, 
                       @Param("failedAt") LocalDateTime failedAt, 
                       @Param("now") LocalDateTime now);
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Job j SET j.status = 'CANCELLED', j.updatedAt = :now, " +
           "j.workerId = null, j.leaseUntil = null, j.startedAt = null, j.scheduledAt = null " +
           "WHERE j.id = :id AND (j.status = 'QUEUED' OR j.status = 'RETRYING')")
    int cancelQueuedOrRetrying(@Param("id") UUID id, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Job j SET j.status = 'CANCELLED', j.updatedAt = :now, " +
           "j.workerId = null, j.leaseUntil = null, j.startedAt = null " +
           "WHERE j.id = :id AND j.status = 'RUNNING' AND j.workerId = :workerId")
    int cancelRunning(@Param("id") UUID id, 
                      @Param("workerId") String workerId, 
                      @Param("now") LocalDateTime now);

    @Query(value = "SELECT DISTINCT type FROM jobs WHERE status = 'QUEUED' OR status = 'RETRYING'", nativeQuery = true)
    List<String> findActiveJobTypes();

    @Query(value = "SELECT COUNT(*) FROM jobs WHERE type = :type AND status = 'RUNNING'", nativeQuery = true)
    int countRunningJobsByType(@Param("type") String type);

    @Query(value = "SELECT COUNT(*) FROM jobs " +
                   "WHERE type = :type " +
                   "  AND ( " +
                   "       (status = 'RUNNING' AND started_at >= :since) " +
                   "       OR (status = 'COMPLETED' AND started_at >= :since) " +
                   "       OR (status IN ('RETRYING', 'DEAD_LETTER') AND last_failed_at >= :since) " +
                   "       OR (status = 'CANCELLED' AND updated_at >= :since) " +
                   "      )", 
           nativeQuery = true)
    int countRecentExecutionsByType(@Param("type") String type, @Param("since") LocalDateTime since);

    @Query(value = "SELECT * FROM jobs " +
                   "WHERE type = :type " +
                   "  AND (status = 'QUEUED' OR status = 'RETRYING') " +
                   "  AND (scheduled_at IS NULL OR scheduled_at <= :now) " +
                   "ORDER BY " +
                   "  CASE WHEN EXTRACT(EPOCH FROM (:now - COALESCE(scheduled_at, created_at))) > :starvationThresholdSeconds THEN 1 ELSE 0 END DESC, " +
                   "  priority DESC, " +
                   "  COALESCE(scheduled_at, created_at) ASC, " +
                   "  id ASC " +
                   "LIMIT :limit " +
                   "FOR UPDATE SKIP LOCKED", 
           nativeQuery = true)
    List<Job> findExecutableJobsByTypeWithLock(
            @Param("type") String type,
            @Param("now") LocalDateTime now, 
            @Param("limit") int limit,
            @Param("starvationThresholdSeconds") long starvationThresholdSeconds);

    @Query("SELECT j.status, COUNT(j) FROM Job j GROUP BY j.status")
    List<Object[]> countJobsByStatus();

    @Query("SELECT COUNT(DISTINCT j.workerId) FROM Job j WHERE j.status = 'RUNNING' AND j.workerId IS NOT NULL")
    long countActiveWorkers();

    @Query(value = "SELECT COALESCE(AVG(EXTRACT(EPOCH FROM (updated_at - started_at)) * 1000), 0), " +
                   "       COALESCE(MAX(EXTRACT(EPOCH FROM (updated_at - started_at)) * 1000), 0) " +
                   "FROM jobs WHERE status = 'COMPLETED'", 
           nativeQuery = true)
    List<Object[]> getCompletedJobDurationMetrics();

    @Query(value = "SELECT COUNT(*) FROM jobs " +
                   "WHERE started_at >= :since " +
                   "   OR (status IN ('RETRYING', 'DEAD_LETTER') AND last_failed_at >= :since) " +
                   "   OR (status = 'CANCELLED' AND updated_at >= :since)", 
           nativeQuery = true)
    long countRecentExecutions(@Param("since") LocalDateTime since);

    @Query(value = "SELECT COUNT(*) FROM jobs WHERE status IN ('RETRYING', 'DEAD_LETTER') AND last_failed_at >= :since", nativeQuery = true)
    long countRecentFailures(@Param("since") LocalDateTime since);

    @Query(value = "SELECT COUNT(*) FROM jobs WHERE status = 'CANCELLED' AND updated_at >= :since", nativeQuery = true)
    long countRecentCancellations(@Param("since") LocalDateTime since);

    List<Job> findByStatus(com.flowforge.enums.JobStatus status);

    @Query(value = "SELECT COALESCE(EXTRACT(EPOCH FROM (:now - MIN(COALESCE(scheduled_at, created_at)))), 0) " +
                   "FROM jobs WHERE status = 'QUEUED' AND (scheduled_at IS NULL OR scheduled_at <= :now)", 
           nativeQuery = true)
    double getOldestQueuedJobAgeSeconds(@Param("now") LocalDateTime now);

    @Query(value = "SELECT COALESCE(EXTRACT(EPOCH FROM (:now - MIN(COALESCE(scheduled_at, created_at)))), 0) " +
                   "FROM jobs WHERE status = 'RETRYING' AND (scheduled_at IS NULL OR scheduled_at <= :now)", 
           nativeQuery = true)
    double getOldestRetryingJobAgeSeconds(@Param("now") LocalDateTime now);

    @Query("SELECT j.type, j.status, COUNT(j) FROM Job j GROUP BY j.type, j.status")
    List<Object[]> getTypeStatusCounts();

    @Query(value = "SELECT type, " +
                   "       COALESCE(AVG(EXTRACT(EPOCH FROM (updated_at - started_at)) * 1000), 0), " +
                   "       COALESCE(MAX(EXTRACT(EPOCH FROM (updated_at - started_at)) * 1000), 0) " +
                   "FROM jobs WHERE status = 'COMPLETED' GROUP BY type", 
           nativeQuery = true)
    List<Object[]> getTypeDurationMetrics();

    @Query(value = "SELECT type, COUNT(*) FROM jobs " +
                   "WHERE started_at >= :since " +
                   "   OR (status IN ('RETRYING', 'DEAD_LETTER') AND last_failed_at >= :since) " +
                   "   OR (status = 'CANCELLED' AND updated_at >= :since) " +
                   "GROUP BY type", 
           nativeQuery = true)
    List<Object[]> getTypeRecentExecutions(@Param("since") LocalDateTime since);

    @Query(value = "SELECT type, COUNT(*) FROM jobs " +
                   "WHERE status IN ('RETRYING', 'DEAD_LETTER') AND last_failed_at >= :since " +
                   "GROUP BY type", 
           nativeQuery = true)
    List<Object[]> getTypeRecentFailures(@Param("since") LocalDateTime since);

    @Query(value = "SELECT type, COUNT(*) FROM jobs " +
                   "WHERE status = 'CANCELLED' AND updated_at >= :since " +
                   "GROUP BY type", 
           nativeQuery = true)
    List<Object[]> getTypeRecentCancellations(@Param("since") LocalDateTime since);
}